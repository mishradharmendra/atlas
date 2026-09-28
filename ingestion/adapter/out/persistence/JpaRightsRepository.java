package com.atlas.ingestion.adapter.out.persistence;

import com.atlas.ingestion.domain.model.PermittedUse;
import com.atlas.ingestion.domain.model.RedistributionClass;
import com.atlas.ingestion.domain.model.RightsMetadata;
import com.atlas.ingestion.domain.model.Use;
import com.atlas.ingestion.domain.port.RightsRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface RightsJpaRepository extends JpaRepository<RightsRow, String> {}

/** Driven adapter: translates between {@link RightsRow} and {@link RightsMetadata}. */
@Repository
class JpaRightsRepository implements RightsRepository {

    private final RightsJpaRepository rows;

    JpaRightsRepository(RightsJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<RightsMetadata> findBySourceId(String sourceId) {
        return rows.findById(sourceId).map(JpaRightsRepository::toDomain);
    }

    @Override
    public void save(RightsMetadata rights) {
        RightsRow row = rows.findById(rights.sourceId()).orElseGet(RightsRow::new);
        row.sourceId = rights.sourceId();
        row.contractId = rights.contractId();
        row.permittedUses = rights.permittedUse().uses().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
        row.maxQuoteChars = rights.permittedUse().quoteCeiling();
        row.embargoUntil = rights.embargoUntil();
        row.redistribution = rights.redistribution().name();
        row.retentionPolicy = rights.retentionPolicy();
        rows.save(row);
    }

    private static RightsMetadata toDomain(RightsRow row) {
        Use[] uses = row.permittedUses == null || row.permittedUses.isBlank()
                ? new Use[0]
                : Arrays.stream(row.permittedUses.split(","))
                        .map(String::trim)
                        .map(Use::valueOf)
                        .toArray(Use[]::new);

        return new RightsMetadata(
                row.sourceId,
                row.contractId,
                PermittedUse.of(row.maxQuoteChars, uses),
                row.embargoUntil,
                RedistributionClass.valueOf(row.redistribution),
                row.retentionPolicy);
    }
}
