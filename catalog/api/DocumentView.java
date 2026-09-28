package com.atlas.catalog.api;

import java.time.Instant;

/** The wire form of a catalogued document. */
public record DocumentView(
        String documentId,
        String sourceId,
        String docType,
        String authority,
        Instant publishedAt,
        String contentHash,
        String storageKey,
        String status,
        String supersededBy,
        String retractionReason) {}
