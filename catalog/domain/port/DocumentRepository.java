package com.atlas.catalog.domain.port;

import com.atlas.catalog.domain.model.Document;
import com.atlas.catalog.domain.model.DocumentId;
import java.util.Optional;

/** Durable store for the catalog. */
public interface DocumentRepository {

    Optional<Document> findById(DocumentId id);

    /**
     * Looks a document up by the hash of its bytes.
     *
     * <p>Acquisition is retried, replayed and backfilled, so the same bytes arrive repeatedly.
     * Content addressing is what makes registration idempotent: without it a replayed feed
     * produces duplicate catalog entries, and an agent counting sources reports the same note
     * three times as three independent confirmations.
     */
    Optional<Document> findByContentHash(String contentHash);

    void save(Document document);
}
