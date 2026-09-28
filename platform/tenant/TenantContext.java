package com.atlas.platform.tenant;

import java.util.Optional;

/**
 * The verified identity of the request being served on this thread.
 *
 * <p>A {@code ThreadLocal} rather than a parameter threaded through every signature. That is the
 * pragmatic trade and it has a real cost — it is invisible in a method's type, and it is wrong on
 * any thread the request did not create. Both are mitigated the same way: {@link #require()}
 * throws rather than returning a default, so code that runs off-request fails loudly instead of
 * quietly serving the wrong tenant.
 *
 * <p>The alternative, passing an identity into every domain call, was rejected because the
 * architecture rules forbid the domain from depending on platform types at all — so the
 * propagation would have to stop at the application layer regardless.
 */
public final class TenantContext {

    private static final ThreadLocal<RequestTenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    static void set(RequestTenant tenant) {
        CURRENT.set(tenant);
    }

    static void clear() {
        // Removed rather than set to null: pooled request threads outlive the request, and a
        // stale identity on a reused thread serves one tenant's data to the next caller.
        CURRENT.remove();
    }

    public static Optional<RequestTenant> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * The current identity, or a refusal.
     *
     * <p>Never returns a default. An "anonymous" or "system" fallback here would make every
     * unauthenticated request look like a valid one to everything downstream.
     */
    public static RequestTenant require() {
        return current().orElseThrow(() -> new UnauthenticatedRequestException(
                "no verified identity on this request; present a signed entitlement snapshot"));
    }

    /** Runs a block as a given identity. For tests and for work dispatched off the request thread. */
    public static void runAs(RequestTenant tenant, Runnable work) {
        RequestTenant previous = CURRENT.get();
        try {
            CURRENT.set(tenant);
            work.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
