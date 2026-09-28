package com.atlas.entitlement.domain.port;

import com.atlas.entitlement.domain.model.Grant;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.util.List;

/**
 * Port for reading durable grants. Implemented in {@code adapter.out.persistence}.
 *
 * <p>Declared in the domain and implemented outside it, so the issuance rules can be tested with a
 * fake in microseconds and the aggregate never learns that a database exists.
 */
public interface GrantRepository {

    /**
     * Every grant in force for this principal at this instant, tenant-wide grants included.
     *
     * <p>Returning both tenant-wide and principal-specific grants in one call is deliberate. Two
     * queries would leave a window in which a barrier added between them is missed, and a
     * missed barrier is a disclosure.
     */
    List<Grant> findInForce(TenantId tenant, PrincipalId principal, AsOf asOf);

    /**
     * Record a grant, or replace one already recorded under the same id.
     *
     * <p>Grants are never edited in place and never deleted. A licence that ends is a grant
     * with an {@code effectiveTo}, because "why did this run see that document?" is answered
     * by replaying what was in force at the run's as-of, and a row that has been removed
     * cannot be replayed. Deleting one makes a past snapshot permanently unexplainable.
     */
    void record(Grant grant);
}
