package com.atlas.tenancy.api;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.util.Optional;

/** Open Host Service for tenancy. */
public interface TenancyApi {

    /**
     * What the tenant was paying at that instant.
     *
     * <p>Takes an {@code asOf} and there is no "current" overload. A margin report for September
     * run in November must use September's seat count, and an API that made the instant optional
     * would default to today's — silently recalculating an invoice that was already sent.
     */
    Optional<SubscriptionView> subscriptionAt(TenantId tenant, AsOf at);

    /** Whether this principal held a seat then. The billable fact. */
    boolean heldSeat(TenantId tenant, PrincipalId principal, AsOf at);

    /** Whether the tenant may start work at all. Suspended customers may not. */
    boolean mayStartRuns(TenantId tenant);
}
