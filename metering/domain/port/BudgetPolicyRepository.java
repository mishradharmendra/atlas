package com.atlas.metering.domain.port;

import com.atlas.metering.domain.model.BudgetPolicy;
import com.atlas.shared.identity.TenantId;
import java.util.Optional;

/** Where per-tenant spend ceilings live. */
public interface BudgetPolicyRepository {

    /**
     * The policy for a period, if one was set.
     *
     * <p>Optional rather than a default, because "no policy" and "a policy of zero" must not be
     * the same value. The first is the ordinary case for a new tenant; the second would refuse
     * everything.
     */
    Optional<BudgetPolicy> find(TenantId tenant, String period);

    void save(BudgetPolicy policy);
}
