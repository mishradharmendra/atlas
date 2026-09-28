package com.atlas.catalog.api;

import java.time.Instant;

/**
 * Open Host Service for the catalog.
 *
 * <p>The answer to "what exists in the corpus". Registration is the point at which acquired bytes
 * become something the platform will admit to holding, and it is deliberately the narrowest
 * possible door: everything that enters the searchable corpus passes through here and is checked
 * against the source's contract on the way.
 */
public interface CatalogApi {

    /**
     * Admits a document to the corpus.
     *
     * <p>Idempotent on content hash: the same bytes registered twice return the existing entry
     * rather than creating a second one.
     *
     * @throws UnlicensedSourceException if the source's contract does not permit indexing, or its
     *     embargo has not expired at {@code at}
     */
    DocumentView register(RegisterDocumentCommand command, Instant at);

    /** Records that a later document replaces this one. The original stays citable. */
    DocumentView supersede(String documentId, String successorId, Instant at);

    /**
     * Withdraws a document and raises the event that drives the cascade.
     *
     * <p>Idempotent: a takedown replayed does not raise a second cascade.
     */
    DocumentView retract(String documentId, String reason, Instant at);

    DocumentView find(String documentId);
}
