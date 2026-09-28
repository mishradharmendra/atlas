package com.atlas.entitlement.application;

import com.atlas.entitlement.domain.model.EntitlementDimension;
import com.atlas.entitlement.domain.model.Grant;
import com.atlas.entitlement.domain.model.GrantEffect;
import com.atlas.entitlement.domain.model.GrantId;
import com.atlas.entitlement.domain.port.GrantRepository;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the durable authorisations snapshots are computed from.
 *
 * <p>This existed only as a read port. {@code IssueEntitlementSnapshot} queried grants and
 * nothing anywhere wrote one, so the table stayed empty, every snapshot authorised nothing, and
 * the platform was unusable in a way that looked exactly like working default-deny.
 */
@Service
public class RecordEntitlementGrant {

    private final GrantRepository grants;

    RecordEntitlementGrant(GrantRepository grants) {
        this.grants = grants;
    }

    @Transactional
    public GrantId record(
            TenantId tenant,
            PrincipalId principal,
            EntitlementDimension dimension,
            String value,
            GrantEffect effect,
            Instant effectiveFrom,
            Instant effectiveTo,
            String contractId) {

        GrantId id = new GrantId(UUID.randomUUID());
        grants.record(new Grant(
                id, tenant, principal, dimension, value, effect, effectiveFrom, effectiveTo,
                contractId));
        return id;
    }
}
