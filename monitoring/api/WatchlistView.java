package com.atlas.monitoring.api;

import java.util.List;
import java.util.Set;

/** A watchlist as callers see it. */
public record WatchlistView(
        String watchlistId,
        String tenantId,
        String ownerId,
        String name,
        Set<String> subjectIds,
        Set<String> relationshipTypes,
        long quietPeriodSeconds,
        int maxPerWindow,
        long windowSeconds,
        boolean active) {}
