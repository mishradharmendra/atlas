package com.atlas.shared.identity;

import java.util.UUID;

/**
 * The acting user or service account.
 *
 * <p>Distinct from {@link TenantId} because entitlement is per-principal, not per-tenant. Two
 * analysts at the same firm can hold different research licences and different internal document
 * ACLs, so "which firm is this" is never enough to decide what may be returned.
 */
public record PrincipalId(UUID value) {

    public PrincipalId {
        if (value == null) {
            throw new IllegalArgumentException("principal id must not be null");
        }
    }

    public static PrincipalId of(String value) {
        return new PrincipalId(UUID.fromString(value));
    }

    public static PrincipalId newId() {
        return new PrincipalId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
