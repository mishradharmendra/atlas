package com.atlas.catalog.api;

import java.time.Instant;

/**
 * A document being admitted to the corpus.
 *
 * @param contentHash the hash of the bytes in the landing zone. Carries the idempotency: the same
 *     bytes registered twice are one document, however many times acquisition replays.
 * @param storageKey where those bytes live. The catalog never holds content — a copy here would
 *     start drifting the first time a re-parse changed anything.
 */
public record RegisterDocumentCommand(
        String documentId,
        String sourceId,
        String docType,
        Instant publishedAt,
        String contentHash,
        String storageKey) {}
