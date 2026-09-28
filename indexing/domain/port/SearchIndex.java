package com.atlas.indexing.domain.port;

/** The serving indexes, as far as this plane needs to know about them. */
public interface SearchIndex {

    /**
     * Removes every span of a document, returning how many were removed.
     *
     * <p>Zero is a valid answer, not an error. Takedowns are replayed and arrive out of order, and
     * a second pass that threw would be retried by the operator until someone gave up on the
     * queue.
     */
    int purgeDocument(String documentId);
}
