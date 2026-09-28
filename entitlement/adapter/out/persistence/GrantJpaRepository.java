package com.atlas.entitlement.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository over {@link GrantRow}. */
interface GrantJpaRepository extends JpaRepository<GrantRow, UUID> {

    /**
     * Grants in force for a principal at an instant, tenant-wide grants included.
     *
     * <p>One query rather than two. Two would leave a window in which a barrier added between them
     * is missed, and a missed barrier is a disclosure.
     *
     * <p>Temporal filtering happens in the database because the alternative -- loading every grant
     * a tenant has ever held and filtering in memory -- degrades linearly with contract history on
     * a path that runs on every query.
     */
    @Query(
            """
            select g from GrantRow g
            where g.tenantId = :tenantId
              and (g.principalId is null or g.principalId = :principalId)
              and (g.effectiveFrom is null or g.effectiveFrom <= :asOf)
              and (g.effectiveTo is null or g.effectiveTo >= :asOf)
            """)
    List<GrantRow> findInForce(
            @Param("tenantId") UUID tenantId,
            @Param("principalId") UUID principalId,
            @Param("asOf") Instant asOf);
}
