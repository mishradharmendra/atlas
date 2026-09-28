package com.atlas.shared.temporal;

import java.time.Instant;
import java.util.Objects;

/**
 * A half-open interval of <em>world</em> time: when something was true, not when we learned it.
 *
 * <p>Half-open — {@code [from, to)} — because closed intervals produce an off-by-one that is
 * invisible in tests and wrong in production. If a supply agreement ends on the 31st and its
 * replacement begins on the 31st, closed intervals make both valid on that day and a traversal
 * returns two contradictory edges. Half-open makes the handover exact and makes "these two
 * intervals abut" distinguishable from "these two intervals overlap".
 *
 * <p>An open end ({@code to == null}) means "still true as far as we know", which is different
 * from "true forever". Nothing in this class treats it as the latter.
 */
public record Validity(Instant from, Instant to) {

    public Validity {
        Objects.requireNonNull(from, "validity start");
        if (to != null && !to.isAfter(from)) {
            throw new IllegalArgumentException(
                    "validity must end after it starts, was [%s, %s)".formatted(from, to));
        }
    }

    /** An interval that has begun and has not been observed to end. */
    public static Validity openFrom(Instant from) {
        return new Validity(from, null);
    }

    public static Validity between(Instant from, Instant to) {
        return new Validity(from, to);
    }

    public boolean contains(AsOf at) {
        Instant instant = at.instant();
        return !instant.isBefore(from) && (to == null || instant.isBefore(to));
    }

    /**
     * Whether the two intervals share any instant.
     *
     * <p>Abutting intervals do not overlap: {@code [Jan, Mar)} and {@code [Mar, Jun)} are a clean
     * handover, and reporting them as a conflict would make every legitimate succession look like
     * contradictory data.
     */
    public boolean overlaps(Validity other) {
        boolean startsBeforeOtherEnds = other.to == null || from.isBefore(other.to);
        boolean otherStartsBeforeThisEnds = to == null || other.from.isBefore(to);
        return startsBeforeOtherEnds && otherStartsBeforeThisEnds;
    }

    public boolean isOpen() {
        return to == null;
    }

    @Override
    public String toString() {
        return "[%s, %s)".formatted(from, to == null ? "open" : to);
    }
}
