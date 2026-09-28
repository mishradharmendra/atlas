package com.atlas.platform.idempotency;

import java.time.Instant;

/** A response already produced for a key, kept so the same key returns the same answer. */
public record IdempotencyRecord(
        String tenantId,
        String key,
        String requestFingerprint,
        int responseStatus,
        String responseBody,
        Instant createdAt) {}
