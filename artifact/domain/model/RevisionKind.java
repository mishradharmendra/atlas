package com.atlas.artifact.domain.model;

/** What kind of change a revision records. */
public enum RevisionKind {

    CELL_BOUND,

    /** A person corrected a value. Kept distinct from a refresh so the two can be counted apart. */
    CELL_OVERRIDDEN,

    /** The system recomputed a value from its source. */
    CELL_REFRESHED,

    ISSUED,

    /**
     * A source this artifact cites was withdrawn.
     *
     * <p>Its own kind rather than a generic note, because "how many issued deliverables did that
     * takedown touch?" is the question a compliance officer asks, and counting it should not
     * require parsing free text.
     */
    SOURCE_RETRACTED,

    WITHDRAWN
}
