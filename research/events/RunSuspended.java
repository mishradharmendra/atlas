package com.atlas.research.events;

import java.time.Instant;

/**
 * A run stopped at a budget ceiling and is waiting for a decision.
 *
 * <p>An event rather than a log line because a suspended run is somebody's work item: it sits
 * there costing nothing and delivering nothing until a person decides whether the question is
 * worth more money. Without a notification, suspended runs silently become abandoned ones and the
 * user concludes the product does not work.
 */
public record RunSuspended(
        String runId,
        String tenantId,
        String principalId,
        String question,
        String reason,
        Instant at) {}
