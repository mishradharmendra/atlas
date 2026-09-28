package com.atlas.entitlement.domain.model;

import java.util.UUID;

/** Identity of an issued entitlement snapshot. Referenced by every run for audit. */
public record SnapshotId(UUID value) {

    public SnapshotId {
        if (value == null) {
            throw new IllegalArgumentException("snapshot id must not be null");
        }
    }

    public static SnapshotId newId() {
        return new SnapshotId(UUID.randomUUID());
    }

    public static SnapshotId of(String value) {
        return new SnapshotId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
