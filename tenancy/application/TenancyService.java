package com.atlas.tenancy.application;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import com.atlas.tenancy.api.SubscriptionView;
import com.atlas.tenancy.api.TenancyApi;
import com.atlas.tenancy.domain.port.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Use cases for tenancy. */
@Service
class TenancyService implements TenancyApi {

    private final TenantRepository tenants;

    TenancyService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<SubscriptionView> subscriptionAt(TenantId tenant, AsOf at) {
        return tenants.findById(tenant)
                .flatMap(found -> found.subscriptionAt(at)
                        .map(subscription -> new SubscriptionView(
                                tenant.value().toString(),
                                subscription.planId(),
                                subscription.seats(),
                                subscription.seatPriceMicros(),
                                subscription.trial())));
    }

    @Override
    public boolean heldSeat(TenantId tenant, PrincipalId principal, AsOf at) {
        return tenants.findById(tenant)
                .map(found -> found.holdsSeatAt(principal, at))
                .orElse(false);
    }

    @Override
    public boolean mayStartRuns(TenantId tenant) {
        // An unknown tenant may not. Defaulting to permitted would make a typo in a tenant id
        // grant access rather than deny it.
        return tenants.findById(tenant).map(found -> found.mayStartRuns()).orElse(false);
    }
}
