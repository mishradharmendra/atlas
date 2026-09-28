package com.atlas.research.api;

import java.util.List;

/** A run as the rest of the platform sees it. */
public record RunView(
        String runId,
        String tenantId,
        String question,
        String skillVersionedId,
        String configFingerprint,
        String status,
        String statusReason,
        String artefactId,
        long totalTokens,
        long totalCostMicros,
        boolean replayable,
        List<String> blockingSteps,
        List<String> abstentions) {}
