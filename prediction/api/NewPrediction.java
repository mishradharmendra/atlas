package com.atlas.prediction.api;

import java.time.Instant;
import java.util.List;

/** What a caller must state to have a prediction recorded. */
public record NewPrediction(
        String tenantId,
        String claim,
        String resolutionCriterion,
        double confidence,
        Instant resolvesAt,
        List<String> sourceIds,
        String artifactId) {}
