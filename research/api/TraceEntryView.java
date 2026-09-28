package com.atlas.research.api;

import java.time.Instant;

/**
 * One step attempt, as a trace viewer renders it.
 *
 * <p>Cost and tokens are per step rather than per run because the question a trace answers is
 * which step was expensive, and a run total cannot answer it. The abstention fields are separate
 * from the observation for the same reason: "could not establish X" is a different thing from
 * what the step saw, and collapsing them into one string makes refusals unsearchable.
 */
public record TraceEntryView(
        int sequence,
        String stepId,
        int attempt,
        Instant startedAt,
        long tookMillis,
        String action,
        String observation,
        String outcome,
        String abstentionReason,
        String abstentionDetail,
        boolean groundTruth,
        long tokens,
        long costMicros) {}
