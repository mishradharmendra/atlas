package com.atlas.research.domain.model;

import java.time.Instant;

/**
 * Raised when an executor tries to drive a run another executor holds.
 *
 * <p>Carries the current holder and expiry rather than a bare message, because the caller's next
 * move depends on both: a lease expiring in four minutes means wait, one held by an executor that
 * died means reclaim. A generic conflict makes both look like a reason to retry in a loop.
 */
public class RunAlreadyClaimedException extends IllegalStateException {

    private final transient String heldBy;
    private final transient Instant expiresAt;

    public RunAlreadyClaimedException(String runId, String heldBy, Instant expiresAt) {
        super("run %s is held by %s until %s".formatted(runId, heldBy, expiresAt));
        this.heldBy = heldBy;
        this.expiresAt = expiresAt;
    }

    public String heldBy() {
        return heldBy;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
