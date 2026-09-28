package com.atlas.metering.domain.model;

import com.atlas.shared.identity.TenantId;
import java.util.Objects;

/**
 * A tenant's spend ceiling for a period, and what happens at it.
 *
 * <h2>Why the action is declared rather than assumed</h2>
 *
 * <p>The right response to a tenant reaching its ceiling is a commercial decision, not an
 * engineering one. Cutting off a strategic account mid-quarter to save a few hundred dollars is
 * worse than the overspend; letting a trial account run unbounded is how a free tier becomes an
 * incident. Both are defensible, and neither should be a constant buried in a service.
 */
public record BudgetPolicy(
        TenantId tenant, String period, long ceilingMicros, BreachAction onBreach) {

    public enum BreachAction {
        /** Record and notify. Nothing is refused. For accounts where cutting off costs more. */
        WARN,

        /**
         * New runs are refused; runs already in flight finish.
         *
         * <p>Killing in-flight runs would discard work already paid for, which spends the money
         * and delivers nothing — the worst of both.
         */
        REFUSE_NEW_RUNS
    }

    public BudgetPolicy {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(onBreach, "breach action");
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("a budget policy must name its period");
        }
        if (ceilingMicros <= 0) {
            throw new IllegalArgumentException(
                    "a ceiling of zero or less refuses everything, which is a disabled tenant "
                            + "rather than a budget; express that as a suspended subscription");
        }
    }

    public boolean isBreachedBy(long spentMicros) {
        return spentMicros >= ceilingMicros;
    }

    /**
     * Whether a new run may start given what has already been spent.
     *
     * <p>Checked before the run rather than during it. A run refused at the ceiling mid-flight
     * has already spent most of what it will spend, so the check would save nothing and lose the
     * work.
     */
    public boolean permitsNewRun(long spentMicros) {
        return onBreach == BreachAction.WARN || !isBreachedBy(spentMicros);
    }

    /** How much of the ceiling is gone. Drives warnings before the wall rather than at it. */
    public double fractionConsumed(long spentMicros) {
        return (double) spentMicros / ceilingMicros;
    }
}
