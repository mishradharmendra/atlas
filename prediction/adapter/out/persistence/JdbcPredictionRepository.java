package com.atlas.prediction.adapter.out.persistence;

import com.atlas.prediction.domain.model.Outcome;
import com.atlas.prediction.domain.model.Prediction;
import com.atlas.prediction.domain.model.PredictionId;
import com.atlas.prediction.domain.model.PredictionStatus;
import com.atlas.prediction.domain.port.PredictionRepository;
import com.atlas.shared.identity.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPredictionRepository implements PredictionRepository {

    private final JdbcTemplate jdbc;

    JdbcPredictionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Prediction prediction) {
        jdbc.update(
                """
                INSERT INTO prediction (prediction_id, tenant_id, claim, resolution_criterion,
                                        confidence, made_at, resolves_at, artifact_id, status,
                                        outcome, resolved_at, resolution_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (prediction_id) DO UPDATE SET
                    status          = EXCLUDED.status,
                    outcome         = EXCLUDED.outcome,
                    resolved_at     = EXCLUDED.resolved_at,
                    resolution_note = EXCLUDED.resolution_note
                """,
                prediction.id().value(),
                prediction.tenant().value(),
                prediction.claim(),
                prediction.resolutionCriterion(),
                prediction.confidence(),
                Timestamp.from(prediction.madeAt()),
                Timestamp.from(prediction.resolvesAt()),
                prediction.artifactId(),
                prediction.status().name(),
                prediction.outcome() == null ? null : prediction.outcome().name(),
                prediction.resolvedAt() == null ? null : Timestamp.from(prediction.resolvedAt()),
                prediction.resolutionNote());

        // The claim and its sources are immutable once recorded, so this only ever runs on the
        // first save. Written idempotently anyway because a retried transaction must not
        // duplicate the attribution and double a source's sample count.
        for (String sourceId : prediction.sourceIds()) {
            jdbc.update(
                    """
                    INSERT INTO prediction_source (prediction_id, source_id) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    prediction.id().value(),
                    sourceId);
        }
    }

    @Override
    public Optional<Prediction> findById(PredictionId id) {
        return hydrate(jdbc.query(
                        "SELECT * FROM prediction WHERE prediction_id = ?",
                        JdbcPredictionRepository::toRow,
                        id.value()))
                .stream()
                .findFirst();
    }

    @Override
    public List<Prediction> restingOn(TenantId tenant, String sourceId) {
        return hydrate(jdbc.query(
                """
                SELECT p.* FROM prediction p
                JOIN prediction_source s ON s.prediction_id = p.prediction_id
                WHERE p.tenant_id = ? AND s.source_id = ?
                """,
                JdbcPredictionRepository::toRow,
                tenant.value(),
                sourceId));
    }

    @Override
    public List<Prediction> dueBefore(Instant now, int limit) {
        return hydrate(jdbc.query(
                """
                SELECT * FROM prediction
                WHERE status = 'OPEN' AND resolves_at <= ?
                ORDER BY resolves_at
                LIMIT ?
                """,
                JdbcPredictionRepository::toRow,
                Timestamp.from(now),
                limit));
    }

    /**
     * Attaches sources to every row in one query rather than one per row.
     *
     * <p>The first version looked up sources inside the row mapper, which made a weight
     * calculation over a thousand predictions a thousand and one round trips. It was invisible in
     * tests, where the sample floor means twenty rows.
     */
    private List<Prediction> hydrate(List<PredictionRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        Object[] ids = rows.stream().map(PredictionRow::id).toArray();

        Map<String, List<String>> sourcesById = new HashMap<>();
        jdbc.query(
                "SELECT prediction_id, source_id FROM prediction_source WHERE prediction_id IN ("
                        + placeholders + ")",
                rs -> {
                    sourcesById
                            .computeIfAbsent(rs.getString("prediction_id"), key -> new ArrayList<>())
                            .add(rs.getString("source_id"));
                },
                ids);

        return rows.stream()
                .map(row -> row.toPrediction(sourcesById.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private static PredictionRow toRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        String outcome = rs.getString("outcome");
        return new PredictionRow(
                rs.getString("prediction_id"),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("claim"),
                rs.getString("resolution_criterion"),
                rs.getDouble("confidence"),
                rs.getTimestamp("made_at").toInstant(),
                rs.getTimestamp("resolves_at").toInstant(),
                rs.getString("artifact_id"),
                PredictionStatus.valueOf(rs.getString("status")),
                outcome == null ? null : Outcome.valueOf(outcome),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getString("resolution_note"));
    }

    private record PredictionRow(
            String id,
            UUID tenantId,
            String claim,
            String criterion,
            double confidence,
            Instant madeAt,
            Instant resolvesAt,
            String artifactId,
            PredictionStatus status,
            Outcome outcome,
            Instant resolvedAt,
            String note) {

        Prediction toPrediction(List<String> sources) {
            Prediction prediction = new Prediction(
                    PredictionId.of(id),
                    new TenantId(tenantId),
                    claim,
                    criterion,
                    confidence,
                    madeAt,
                    resolvesAt,
                    sources,
                    artifactId);

            // Rehydrated rather than replayed through resolve(): replaying would re-apply the
            // not-graded-early rule against a clock that has since moved, and would refuse to
            // load rows the same rule allowed to be written.
            prediction.rehydrate(status, outcome, resolvedAt, note);
            return prediction;
        }
    }
}
