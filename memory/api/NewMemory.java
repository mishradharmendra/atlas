package com.atlas.memory.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A request to remember something.
 *
 * <p>{@code expiresAt} is nullable and that is deliberate for preferences, which do not decay.
 * {@link com.atlas.memory.domain.model.MemoryKind#PRIOR_CONCLUSION} without one is accepted by
 * the aggregate and is nearly always a mistake, which is why the service supplies a default.
 */
public record NewMemory(
        String tenantId,
        String kind,
        String text,
        String writtenByRun,
        Set<String> sourceDocIds,
        Set<String> entitlementTags,
        Instant expiresAt) {}
