package com.atlas.entitlement.domain.port;

import com.atlas.entitlement.domain.model.EntitlementSnapshot;

/**
 * Port for signing and verifying snapshots.
 *
 * <p>The signature is what lets the Python retrieval plane trust a token it did not compute. Both
 * planes run inside the same trust boundary today, which makes this look like ceremony — it is
 * not. The snapshot travels through an agent that assembles context from many sources, and an
 * agent able to widen its own entitlement by editing a field it holds in memory is a privilege
 * escalation with no audit trail.
 *
 * <p>Verification failure is fatal and never degrades to an unfiltered search.
 */
public interface SnapshotSigner {

    /** Returns the snapshot with its signature attached. */
    EntitlementSnapshot sign(EntitlementSnapshot unsigned);

    /**
     * Whether the signature matches the snapshot's contents.
     *
     * <p>Implementations must compare in constant time. A short-circuiting comparison leaks the
     * signature a byte at a time to anything able to measure response latency.
     */
    boolean verify(EntitlementSnapshot snapshot);
}
