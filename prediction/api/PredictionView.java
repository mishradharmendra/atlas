package com.atlas.prediction.api;

import java.time.Instant;
import java.util.List;

/** A recorded prediction as other modules see it. */
public record PredictionView(
        String predictionId,
        String tenantId,
        String claim,
        String resolutionCriterion,
        double confidence,
        Instant madeAt,
        Instant resolvesAt,
        List<String> sourceIds,
        String artifactId,
        String status,
        String outcome,
        Instant resolvedAt,
        String resolutionNote,
        Double brierScore) {}
