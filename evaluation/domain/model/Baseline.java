package com.atlas.evaluation.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A pinned set of results that later runs are compared against.
 *
 * <p>Immutable. Comparing against "the last run" instead produces a benchmark that can only detect
 * a cliff: quality sliding by one percent a week is never a regression against yesterday, and after
 * two months the system is materially worse with every build green.
 *
 * @param metrics keyed by slice. {@code "overall"} plus one entry per task type and hazard, because
 *     the aggregate is where a collapsed query class hides.
 * @param costPerQuestionMicros including retrieval-loop tokens. Excluding them makes a system that
 *     retrieves five times per question look identical to one that retrieves once, right up to the
 *     invoice.
 */
public record Baseline(
        BaselineId id,
        String benchmark,
        int rubricVersion,
        Instant pinnedAt,
        Map<String, Double> metrics,
        long costPerQuestionMicros) {

    public Baseline {
        Objects.requireNonNull(id, "baseline id");
        if (benchmark == null || benchmark.isBlank()) {
            throw new IllegalArgumentException("a baseline belongs to a named benchmark");
        }
        Objects.requireNonNull(pinnedAt, "pinned at");
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException(
                    "a baseline with no metrics cannot detect a regression in anything");
        }
        if (rubricVersion < 1) {
            throw new IllegalArgumentException(
                    "a baseline is only meaningful against the rubric version that produced it");
        }
        if (costPerQuestionMicros < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        metrics = Map.copyOf(metrics);
    }
}
