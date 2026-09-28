package com.atlas.shared.identity;

import java.util.UUID;

/**
 * The customer organisation. Every read path in the platform is scoped by one.
 *
 * <p>There is no "default tenant" and no unscoped query. A retrieval call, a memory lookup and an
 * artefact read all require a tenant, because the alternative — an ambient default that works in
 * development — is precisely the bug that leaks one customer's research into another's.
 */
public record TenantId(UUID value) {

    public TenantId {
        if (value == null) {
            throw new IllegalArgumentException("tenant id must not be null");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    public static TenantId newId() {
        return new TenantId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
