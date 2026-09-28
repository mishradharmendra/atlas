package com.atlas.entitlement.domain.model;

import java.util.UUID;

/** Identity of a durable grant record. */
public record GrantId(UUID value) {

    public GrantId {
        if (value == null) {
            throw new IllegalArgumentException("grant id must not be null");
        }
    }

    public static GrantId newId() {
        return new GrantId(UUID.randomUUID());
    }

    public static GrantId of(String value) {
        return new GrantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
