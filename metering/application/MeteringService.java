package com.atlas.metering.application;

import com.atlas.metering.api.MeteringApi;
import com.atlas.metering.domain.model.BudgetPolicy;
import com.atlas.metering.domain.model.TenantUsage;
import com.atlas.metering.domain.port.BudgetPolicyRepository;
import com.atlas.metering.domain.port.UsageLedger;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import com.atlas.tenancy.api.TenancyApi;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Prices consumption against the subscription that was actually in force.
 *
 * <p>The ledger knows what was consumed; tenancy knows what was being paid. Keeping the two apart
 * is what stops the margin figure from being whatever its caller supplies — the previous version
 * took seats and seat price as parameters, and its only caller was a test.
 */
@Service
class MeteringService implements MeteringApi {

    private final UsageLedger ledger;
    private final BudgetPolicyRepository policies;
    private final TenancyApi tenancy;

    MeteringService(UsageLedger ledger, BudgetPolicyRepository policies, TenancyApi tenancy) {
        this.ledger = ledger;
        this.policies = policies;
        this.tenancy = tenancy;
    }

    @Override
    public TenantUsage marginFor(TenantId tenant, String period, AsOf subscriptionAsOf) {
        TenantUsage consumption = ledger.usageFor(tenant, period);

        // Unpriced when there was no subscription in force: a trial or a gap between contracts.
        // Pricing those at zero revenue would report them as infinitely unprofitable and bury
        // the tenants that genuinely are.
        return tenancy.subscriptionAt(tenant, subscriptionAsOf)
                .map(subscription -> consumption.pricedAt(
                        subscription.seats(), subscription.seatPriceMicros()))
                .orElse(consumption);
    }

    @Override
    public boolean permitsNewRun(TenantId tenant, String period) {
        if (!tenancy.mayStartRuns(tenant)) {
            return false;
        }
        Optional<BudgetPolicy> policy = policies.find(tenant, period);
        if (policy.isEmpty()) {
            // No policy is not an implicit ceiling of zero. A tenant nobody has configured a
            // budget for is the common case, and refusing them would make metering a gate that
            // has to be set up before the platform works at all.
            return true;
        }
        return policy.get().permitsNewRun(ledger.spentMicros(tenant, period));
    }
}
