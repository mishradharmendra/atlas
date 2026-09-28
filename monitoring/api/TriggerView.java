package com.atlas.monitoring.api;

import java.time.Instant;

/**
 * A trigger as callers see it.
 *
 * <p>{@code suppressionReason} is part of the view rather than an internal detail. A watcher
 * asking why they were not told deserves the answer, and an operator tuning a noisy list needs to
 * see which of the three mechanisms is doing the work.
 */
public record TriggerView(
        String triggerId,
        String watchlistId,
        String subjectId,
        String changeKind,
        String factKey,
        String summary,
        Instant observedAt,
        String status,
        String suppressionReason,
        Instant deliveredAt) {}
