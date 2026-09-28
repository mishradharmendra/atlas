package com.atlas.platform.tenant;

/**
 * Raised when a request's body names a different tenant from the one it authenticated as.
 *
 * <p>Refused rather than silently corrected to the verified tenant. Quietly overriding the field
 * would make a cross-tenant attempt succeed against the attacker's own data and leave no trace;
 * refusing it makes the attempt visible in logs and metrics, which is the only way anyone finds
 * out it is happening.
 */
public class TenantMismatchException extends RuntimeException {

    private final transient String authenticatedAs;
    private final transient String requested;

    public TenantMismatchException(String authenticatedAs, String requested) {
        super("request authenticated as tenant %s but names tenant %s"
                .formatted(authenticatedAs, requested));
        this.authenticatedAs = authenticatedAs;
        this.requested = requested;
    }

    public String authenticatedAs() {
        return authenticatedAs;
    }

    public String requested() {
        return requested;
    }
}
