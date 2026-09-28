package com.atlas.shared.budget;

import java.time.Duration;

/**
 * The spend ceiling for a single agent run, enforced monotonically.
 *
 * <h2>Why this is a domain-shaped policy object and not a config value</h2>
 *
 * <p>Seat-based pricing combined with always-on agents is structurally dangerous: revenue is flat
 * per seat while cost scales with autonomy and question complexity. A single unbounded run can
 * consume a month of one seat's margin, and the usual culprit is a model compensating for weak
 * retrieval by fetching more — longer contexts, more duplicates, worse synthesis, higher cost, all
 * at once.
 *
 * <p>The three dimensions are enforced together because they fail independently. A run can be
 * cheap in tokens but stuck in a retry loop, or fast but enormously expensive.
 *
 * <p>Note that token price is not the metric. A model that is cheap per token but burns four times
 * as many tokens assembling context is more expensive per <em>question</em>, and questions are what
 * get delivered. {@code tokensConsumed} is therefore tracked as tokens-to-completion for the whole
 * run, not per call.
 */
public final class RunBudget {

    private final long maxTokens;
    private final Duration maxWallClock;
    private final long maxSpendMicros;

    private long tokensConsumed;
    private Duration elapsed = Duration.ZERO;
    private long spentMicros;

    public RunBudget(long maxTokens, Duration maxWallClock, long maxSpendMicros) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("token ceiling must be positive");
        }
        if (maxWallClock == null || maxWallClock.isNegative() || maxWallClock.isZero()) {
            throw new IllegalArgumentException("wall-clock ceiling must be a positive duration");
        }
        if (maxSpendMicros <= 0) {
            throw new IllegalArgumentException("spend ceiling must be positive");
        }
        this.maxTokens = maxTokens;
        this.maxWallClock = maxWallClock;
        this.maxSpendMicros = maxSpendMicros;
    }

    /**
     * Records consumption and fails the run if any ceiling is breached.
     *
     * <p>Consumption is recorded <em>before</em> the check so the ledger reflects what was actually
     * spent even on the call that breached. Under-reporting the final call would make cost
     * reconciliation drift from the provider's invoice.
     */
    public void consume(long tokens, Duration duration, long spendMicros) {
        if (tokens < 0 || spendMicros < 0 || duration.isNegative()) {
            throw new IllegalArgumentException("consumption must not be negative");
        }
        tokensConsumed += tokens;
        elapsed = elapsed.plus(duration);
        spentMicros += spendMicros;

        if (tokensConsumed > maxTokens) {
            throw new BudgetExceededException("tokens", maxTokens, tokensConsumed);
        }
        if (elapsed.compareTo(maxWallClock) > 0) {
            throw new BudgetExceededException(
                    "wall_clock_millis", maxWallClock.toMillis(), elapsed.toMillis());
        }
        if (spentMicros > maxSpendMicros) {
            throw new BudgetExceededException("spend_micros", maxSpendMicros, spentMicros);
        }
    }

    /**
     * Whether another step of the given estimated size would fit.
     *
     * <p>Lets a planner stop cleanly at a step boundary instead of discovering the ceiling
     * mid-synthesis and abandoning half-written work.
     */
    public boolean canAfford(long estimatedTokens, long estimatedSpendMicros) {
        return tokensConsumed + estimatedTokens <= maxTokens
                && spentMicros + estimatedSpendMicros <= maxSpendMicros;
    }

    public long tokensConsumed() {
        return tokensConsumed;
    }

    public long maxTokens() {
        return maxTokens;
    }

    public Duration maxWallClock() {
        return maxWallClock;
    }

    public long maxSpendMicros() {
        return maxSpendMicros;
    }

    public long spentMicros() {
        return spentMicros;
    }

    public Duration elapsed() {
        return elapsed;
    }

    public double fractionConsumed() {
        double byTokens = (double) tokensConsumed / maxTokens;
        double bySpend = (double) spentMicros / maxSpendMicros;
        double byTime = (double) elapsed.toMillis() / maxWallClock.toMillis();
        return Math.max(byTokens, Math.max(bySpend, byTime));
    }
}
