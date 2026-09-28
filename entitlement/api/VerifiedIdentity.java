package com.atlas.entitlement.api;

import java.time.Instant;

/**
 * Who a request is, established by verifying a signed snapshot rather than by believing a header.
 *
 * <p>The distinction is the whole point. Before this existed, every endpoint took the tenant id
 * from the request body — so a caller could name any tenant and the platform would serve them.
 * That is broken access control of the plainest kind: the subject asserts its own identity.
 */
public record VerifiedIdentity(
        String snapshotId,
        String tenantId,
        String principalId,
        Instant expiresAt,
        java.util.Set<String> deniedLabels) {

    public VerifiedIdentity {
        deniedLabels = java.util.Set.copyOf(deniedLabels);
    }
}
