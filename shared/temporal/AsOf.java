package com.atlas.shared.temporal;

import java.time.Instant;

/**
 * An explicit point in time that a question is being asked about.
 *
 * <p>Required on every query and every graph traversal. There is deliberately no "current"
 * default, and that absence is the design: what was true in Q2 must not be silently assumed true
 * in Q4, and a supplier relationship that ended two years ago must not contaminate this quarter's
 * analysis.
 *
 * <p>The failure mode this prevents is subtle and expensive. A system that defaults to "now"
 * produces answers that cannot be reproduced tomorrow, which means a run cannot be replayed, a
 * regression cannot be bisected, and a client cannot be shown why a number changed.
 */
public record AsOf(Instant instant) implements Comparable<AsOf> {

    public AsOf {
        if (instant == null) {
            throw new IllegalArgumentException("as-of instant must not be null");
        }
    }

    public static AsOf at(Instant instant) {
        return new AsOf(instant);
    }

    public boolean isBefore(AsOf other) {
        return instant.isBefore(other.instant);
    }

    public boolean isAfter(AsOf other) {
        return instant.isAfter(other.instant);
    }

    @Override
    public int compareTo(AsOf other) {
        return instant.compareTo(other.instant);
    }

    @Override
    public String toString() {
        return instant.toString();
    }
}
