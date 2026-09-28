package com.atlas.tenancy.domain.model;

/** Whether a tenant may use the platform, and why not if not. */
public enum TenantStatus {

    /** Evaluating. Real access, no revenue — which is why margin reports must exclude them. */
    TRIAL,

    ACTIVE,

    /**
     * Access withdrawn, data retained.
     *
     * <p>Kept distinct from {@link #CLOSED} because the remedies differ: a suspension is usually
     * an unpaid invoice and is reversed by payment, while a closure starts a retention clock.
     * Collapsing them means a customer who paid late finds their data deleted.
     */
    SUSPENDED,

    /** Contract ended. Terminal. */
    CLOSED;

    public boolean permitsUse() {
        return this == TRIAL || this == ACTIVE;
    }
}
