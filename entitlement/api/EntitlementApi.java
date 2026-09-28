package com.atlas.entitlement.api;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;

/**
 * Open Host Service for entitlement.
 *
 * <p>The one entry point other modules and the Python plane use. Everything behind it is internal.
 */
public interface EntitlementApi {

    /**
     * Issues a signed snapshot authorising this principal at this point in time.
     *
     * @throws IllegalArgumentException if {@code asOf} is in the future, which would authorise
     *     grants that have not commenced
     */
    EntitlementSnapshotView issue(TenantId tenant, PrincipalId principal, AsOf asOf);
}
