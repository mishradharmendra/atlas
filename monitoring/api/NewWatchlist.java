package com.atlas.monitoring.api;

import java.util.List;
import java.util.Set;

/** What a caller must say to create a watchlist. */
public record NewWatchlist(
        String tenantId,
        String principalId,
        String name,
        Set<String> subjectIds,
        Set<String> relationshipTypes,
        long quietPeriodSeconds,
        int maxPerWindow,
        long windowSeconds) {}
