package com.atlas.platform.tenant;

import java.util.Objects;

/**
 * Who the current request is, after verification.
 *
 * <p>Deliberately not constructible from a header value. The only thing that produces one is
 * {@code TenantContextFilter}, after a signature check — which is what stops "the caller said so"
 * from being an identity.
 */
public record RequestTenant(
        String tenantId,
        String principalId,
        String snapshotId,
        java.util.Set<String> deniedLabels) {

    public RequestTenant {
        Objects.requireNonNull(tenantId, "tenant id");
        Objects.requireNonNull(principalId, "principal id");
        deniedLabels = java.util.Set.copyOf(Objects.requireNonNullElse(deniedLabels, java.util.Set.of()));
    }

    /** Whether this credential is barred from a classification, rather than merely lacking it. */
    public boolean isDenied(String label) {
        return deniedLabels.contains(label.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Whether this request may act on data belonging to the named tenant.
     *
     * <p>Exact match only. There is no notion of a support or admin tenant that may act on
     * others: that is a real requirement and it belongs behind an explicit, audited impersonation
     * flow, not behind a special case here where it would apply to every endpoint silently.
     */
    public boolean mayActOn(String otherTenantId) {
        return tenantId.equals(otherTenantId);
    }
}
