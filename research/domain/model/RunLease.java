package com.atlas.research.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An executor's exclusive claim on a run, valid until it expires.
 *
 * <h2>Why a lease rather than a workflow engine</h2>
 *
 * <p>The problem G5.6 names is narrow and specific: an executor dies mid-run, and the run sits
 * {@code RUNNING} for ever. Nobody is told, the user waits, and the budget stays reserved against
 * work that stopped hours ago.
 *
 * <p>A workflow engine solves that by owning execution. It also brings a server, a worker
 * deployment, a second source of truth about run state, and a migration path for every change to
 * the step protocol — for a run whose steps already persist their own forward-recovery trace.
 * The lease gets the same guarantee from the database that already holds the saga: an executor
 * that stops renewing is detectably gone, and the run becomes reclaimable.
 *
 * <p>The trade is real and worth naming. A lease gives durability and detection; it does not give
 * automatic retry orchestration, timers, or signals. Those are what would justify the engine, and
 * none of them is what an orphaned run needs.
 *
 * <h2>Why the holder is checked on every write</h2>
 *
 * <p>Without it, a reclaimed run has two executors writing to it: the original, which is not dead
 * but merely slow, and the replacement. Both append steps, both charge the budget, and the trace
 * interleaves two attempts at the same work. That is worse than the orphan — it is an orphan
 * whose output looks complete.
 */
public record RunLease(String heldBy, Instant expiresAt) {

    /**
     * Long enough to survive a slow model call, short enough that an orphan is noticed.
     *
     * <p>Under this, a step that legitimately takes two minutes loses its lease mid-call and a
     * second executor starts the same work. Much over it, and a crashed worker holds the run past
     * the point a user gives up waiting.
     */
    public static final Duration DEFAULT_DURATION = Duration.ofMinutes(5);

    public RunLease {
        if (heldBy == null || heldBy.isBlank()) {
            throw new IllegalArgumentException(
                    "a lease must name its holder; an anonymous lease cannot be renewed by the "
                            + "executor that owns it or refused to the one that does not");
        }
        Objects.requireNonNull(expiresAt, "lease expiry");
    }

    public static RunLease granted(String executorId, Instant now, Duration duration) {
        return new RunLease(executorId, now.plus(duration));
    }

    public boolean isHeldBy(String executorId) {
        return heldBy.equals(executorId);
    }

    public boolean hasExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public RunLease renewedAt(Instant now, Duration duration) {
        return new RunLease(heldBy, now.plus(duration));
    }
}
