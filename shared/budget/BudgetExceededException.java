package com.atlas.shared.budget;

/**
 * Raised when a run exceeds its token, wall-clock or spend ceiling.
 *
 * <p>Deliberately an exception rather than a silently truncated context. The tempting alternative —
 * quietly dropping the lowest-ranked evidence to fit the remaining budget — produces an answer that
 * looks complete, cites real sources, and is missing the thing that mattered. A run that stops and
 * says so is recoverable; a run that silently degrades is not detectable at the point of use.
 */
public class BudgetExceededException extends RuntimeException {

    private final String dimension;
    private final long limit;
    private final long attempted;

    public BudgetExceededException(String dimension, long limit, long attempted) {
        super("budget exceeded on %s: limit %d, attempted %d".formatted(dimension, limit, attempted));
        this.dimension = dimension;
        this.limit = limit;
        this.attempted = attempted;
    }

    public String dimension() {
        return dimension;
    }

    public long limit() {
        return limit;
    }

    public long attempted() {
        return attempted;
    }
}
