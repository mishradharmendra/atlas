package com.atlas.monitoring.domain.model;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * What a watchlist considers worth interrupting someone for.
 *
 * <p>Mandatory before a watchlist can be activated. A watchlist with no rule is a subscription to
 * everything, which is the default that produces a firehose on day one and a muted channel on day
 * two — and a muted channel is indistinguishable from a working one until something is missed.
 */
public record MaterialityRule(
        Set<String> relationshipTypes, Duration quietPeriod, int maxPerWindow, Duration window) {

    public MaterialityRule {
        // "Everything about Acme" is the setting that gets muted. Naming the kinds of change
        // forces the decision at setup, when the user is thinking about it, rather than at 2am
        // when they are deciding whether to keep the alerts on at all.
        if (relationshipTypes == null || relationshipTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "a materiality rule must name at least one relationship type; a watchlist for "
                            + "every kind of change is one nobody keeps switched on");
        }
        Objects.requireNonNull(quietPeriod, "quiet period");
        Objects.requireNonNull(window, "window");

        if (quietPeriod.isNegative()) {
            throw new IllegalArgumentException("quiet period must not be negative");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("the fan-out window must be a positive duration");
        }
        if (maxPerWindow < 1) {
            throw new IllegalArgumentException(
                    ("a cap of %d would suppress everything, which is a muted watchlist wearing "
                                    + "the costume of a configured one")
                            .formatted(maxPerWindow));
        }
        relationshipTypes = Set.copyOf(relationshipTypes);
    }

    /** Whether this kind of change is one the watcher asked about. */
    public boolean covers(String relationshipType) {
        return relationshipTypes.contains(relationshipType);
    }
}
