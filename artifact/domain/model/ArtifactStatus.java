package com.atlas.artifact.domain.model;

/** Where a deliverable is in its life. */
public enum ArtifactStatus {

    /** Being built. Cells may still be added and the whole thing may be discarded. */
    DRAFT,

    /**
     * Sent. Immutable in content; only flags may still be attached.
     *
     * <p>The distinction the model exists to make: you cannot un-send a deck. Once issued, a
     * correction is a new revision of a new artifact, never an edit to this one.
     */
    ISSUED,

    /**
     * Issued, and something it rests on has since been withdrawn or restated.
     *
     * <p>Not an error state and not a correction. It is the platform telling whoever sent this
     * that the ground moved underneath it — which is only possible because every cell records
     * the span it came from.
     */
    FLAGGED,

    /** Formally withdrawn. Terminal. */
    WITHDRAWN;

    public boolean isSent() {
        return this == ISSUED || this == FLAGGED || this == WITHDRAWN;
    }
}
