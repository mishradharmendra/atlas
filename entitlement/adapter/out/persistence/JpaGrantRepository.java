package com.atlas.entitlement.adapter.out.persistence;

import com.atlas.entitlement.domain.model.EntitlementDimension;
import com.atlas.entitlement.domain.model.Grant;
import com.atlas.entitlement.domain.model.GrantEffect;
import com.atlas.entitlement.domain.model.GrantId;
import com.atlas.entitlement.domain.port.GrantRepository;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Driven adapter: translates between {@link GrantRow} and the domain's {@link Grant}.
 *
 * <p>The translation is the adapter's whole job. Because it exists, the domain never learns that
 * a dimension is stored as a string or that a tenant-wide grant is represented by a null column.
 */
@Repository
class JpaGrantRepository implements GrantRepository {

    private final GrantJpaRepository rows;

    JpaGrantRepository(GrantJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public List<Grant> findInForce(TenantId tenant, PrincipalId principal, AsOf asOf) {
        return rows.findInForce(tenant.value(), principal.value(), asOf.instant()).stream()
                .map(JpaGrantRepository::toDomain)
                .toList();
    }

    @Override
    public void record(Grant grant) {
        GrantRow row = new GrantRow();
        row.grantId = grant.id().value();
        row.tenantId = grant.tenant().value();
        row.principalId = grant.isTenantWide() ? null : grant.principal().value();
        row.dimension = grant.dimension().name();
        row.grantValue = grant.value();
        row.effect = grant.effect().name();
        row.effectiveFrom = grant.effectiveFrom();
        row.effectiveTo = grant.effectiveTo();
        row.contractId = grant.contractId();
        rows.save(row);
    }

    private static Grant toDomain(GrantRow row) {
        return new Grant(
                new GrantId(row.grantId),
                new TenantId(row.tenantId),
                row.principalId == null ? null : new PrincipalId(row.principalId),
                EntitlementDimension.valueOf(row.dimension),
                row.grantValue,
                GrantEffect.valueOf(row.effect),
                row.effectiveFrom,
                row.effectiveTo,
                row.contractId);
    }
}
