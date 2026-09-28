package com.atlas.research.domain.model;

/** Where a run is. */
public enum RunStatus {

    PLANNED,
    RUNNING,

    /**
     * Stopped at a budget ceiling, resumable once a human raises it.
     *
     * <p>Not a failure and, crucially, not a completion. Returning what had accumulated at the
     * ceiling would produce an artefact indistinguishable from a complete one — same shape, same
     * confidence, three sources short — and the person acting on it would have no way to tell.
     */
    SUSPENDED,

    /** Every step reached a terminal state and the run may emit its artefact. */
    COMPLETED,

    /** A step with {@code ABORT} failed. Nothing downstream would have been trustworthy. */
    FAILED,

    /** Cancelled by a person. Distinguished from FAILED so operational metrics stay honest. */
    ABANDONED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == ABANDONED;
    }
}
