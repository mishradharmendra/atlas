package com.atlas.prediction.domain.model;

/** Where a prediction is in its life. */
public enum PredictionStatus {

    /** Recorded, not yet due. */
    OPEN,

    /** Graded against its criterion. Terminal. */
    RESOLVED,

    /**
     * Past its resolution date with nobody having graded it.
     *
     * <p>Kept apart from OPEN so the scoreboard can show what it is ignoring. A forecast record
     * that silently leaves overdue predictions open reports only the ones someone bothered to
     * grade, and the ones nobody bothers with are disproportionately the embarrassing ones.
     */
    OVERDUE
}
