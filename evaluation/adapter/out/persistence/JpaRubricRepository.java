package com.atlas.evaluation.adapter.out.persistence;

import com.atlas.evaluation.domain.model.Axis;
import com.atlas.evaluation.domain.model.Rubric;
import com.atlas.evaluation.domain.model.RubricCriterion;
import com.atlas.evaluation.domain.model.RubricId;
import com.atlas.evaluation.domain.model.RubricStatus;
import com.atlas.evaluation.domain.model.Weight;
import com.atlas.evaluation.domain.port.RubricRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

interface RubricJpaRepository extends JpaRepository<RubricRow, RubricRow.Key> {

    @Query(
            """
            select r from RubricRow r
            where r.rubricId = :rubricId and r.status = 'FROZEN'
            order by r.version desc
            limit 1
            """)
    Optional<RubricRow> findLatestFrozen(String rubricId);
}

/** Driven adapter for rubrics. */
@Repository
class JpaRubricRepository implements RubricRepository {

    /**
     * Deliberately not the application's mapper.
     *
     * <p>This is a storage format, and a frozen rubric must read back exactly as it was written.
     * Sharing the HTTP mapper would put the stored bytes at the mercy of a global serialisation
     * setting changed for an unrelated endpoint -- and the symptom would be old rubrics failing to
     * load, years after the change that caused it.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RubricJpaRepository rows;

    JpaRubricRepository(RubricJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Rubric> find(RubricId id, int version) {
        return rows.findById(new RubricRow.Key(id.value(), version)).map(this::toDomain);
    }

    @Override
    public Optional<Rubric> findLatestFrozen(RubricId id) {
        return rows.findLatestFrozen(id.value()).map(this::toDomain);
    }

    @Override
    public void save(Rubric rubric) {
        RubricRow row = rows.findById(new RubricRow.Key(rubric.id().value(), rubric.version()))
                .orElseGet(RubricRow::new);

        if (RubricStatus.FROZEN.name().equals(row.status)) {
            // The aggregate refuses edits; so does the store. Two guards rather than one because
            // a bulk loader or a migration script never passes through the aggregate.
            throw new IllegalStateException(
                    ("rubric %s v%d is frozen and cannot be overwritten; every number reported "
                                    + "under it was produced by its criteria")
                            .formatted(rubric.id(), rubric.version()));
        }

        row.rubricId = rubric.id().value();
        row.version = rubric.version();
        row.question = rubric.question();
        row.asOf = rubric.asOf();
        row.status = rubric.status().name();
        row.frozenAt = rubric.frozenAt();
        row.criteria = write(rubric.criteria());
        rows.save(row);
    }

    private Rubric toDomain(RubricRow row) {
        Rubric rubric = new Rubric(RubricId.of(row.rubricId), row.question, row.asOf, row.version);
        read(row.criteria).forEach(rubric::addCriterion);
        rubric.rehydrate(RubricStatus.valueOf(row.status), row.frozenAt);
        return rubric;
    }

    private record CriterionDto(String id, String axis, String statement, int weight) {}

    private String write(List<RubricCriterion> criteria) {
        try {
            return JSON.writeValueAsString(criteria.stream()
                    .map(c -> new CriterionDto(
                            c.id(), c.axis().name(), c.statement(), c.weight().value()))
                    .toList());
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise rubric criteria", e);
        }
    }

    private List<RubricCriterion> read(String raw) {
        try {
            return JSON.readValue(raw, new TypeReference<List<CriterionDto>>() {}).stream()
                    .map(d -> new RubricCriterion(
                            d.id(), Axis.valueOf(d.axis()), d.statement(), Weight.of(d.weight())))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("could not read rubric criteria", e);
        }
    }
}
