package com.atlas.ingestion.domain.model;

/**
 * A single, independently grantable permission over a document.
 *
 * <p>Modelled as an enum rather than a set of booleans on one object so that a permission can be
 * named in an audit log, revoked individually, and — critically — so that adding a new kind of use
 * later is a compile-time event at every call site that switches over them.
 */
public enum Use {

    /** Include the text in a lexical index. The permission almost every licence grants. */
    INDEX_LEXICAL,

    /**
     * Compute and store embeddings.
     *
     * <p>Distinct from lexical indexing because a vector is a derived representation that outlives
     * the source text and is not human-readable — several publishers treat it differently, and some
     * have declined it outright.
     */
    INDEX_DENSE,

    /**
     * Extract entities and relationships into the knowledge graph.
     *
     * <p>The most contentious of the six. Extracted facts are generally not copyrightable, but a
     * graph built from an entire licensed corpus sits in contested territory, and the edges persist
     * long after the document that produced them would have expired.
     */
    EXTRACT_TO_GRAPH,

    /** Reproduce the text verbatim in an answer, subject to a length ceiling. */
    QUOTE_VERBATIM,

    /** Carry content into a deliverable the customer takes away from the platform. */
    EXPORT_TO_ARTIFACT,

    /**
     * Use as training data.
     *
     * <p>Defaults to denied everywhere and is unconditionally denied for tenant-private content —
     * see {@link PermittedUse#forTenantContent()}. A platform that trains on customer material has
     * a commercial problem long before it has a legal one.
     */
    TRAIN_MODELS
}
