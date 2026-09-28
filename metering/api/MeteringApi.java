package com.atlas.metering.api;

import com.atlas.metering.domain.model.TenantUsage;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;

/** Open Host Service for metering. */
public interface MeteringApi {

    /**
     * What a tenant cost in a period, priced against the subscription they actually held then.
     *
     * <p>The {@code asOf} picks the subscription, not the usage. Running September's report in
     * November must use September's seat count; using today's silently recalculates an invoice
     * that has already been sent, which is the failure temporal subscriptions exist to prevent.
     */
    TenantUsage marginFor(TenantId tenant, String period, AsOf subscriptionAsOf);

    /** Whether the tenant's budget policy permits a new run to start. */
    boolean permitsNewRun(TenantId tenant, String period);
}
