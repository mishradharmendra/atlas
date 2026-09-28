package com.atlas.entitlement.api;

import java.time.Instant;
import java.util.List;

/**
 * The wire form of an entitlement snapshot.
 *
 * <p>Deliberately a transport type rather than the domain aggregate. Publishing the aggregate
 * would make every field of it part of a contract consumed by another plane, and a contract is far
 * harder to change than an internal model — the domain would end up frozen by its own API.
 *
 * <p>Mirrors {@code contracts/proto/atlas/v1/entitlement.proto}.
 */
public record EntitlementSnapshotView(
        String snapshotId,
        String tenantId,
        String principalId,
        Instant asOf,
        Instant expiresAt,
        List<TagView> allow,
        List<TagView> deny,
        String signature) {

    /** One grant or denial, with its validity window preserved. */
    public record TagView(String dimension, String value, Instant validFrom, Instant validTo) {}
}
