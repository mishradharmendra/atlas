package com.atlas.research.domain.model;

/** How one step of a run ended. */
public enum StepOutcome {

    PENDING,

    /** Produced a conclusion. */
    CONCLUDED,

    /**
     * Declined to conclude, and said why.
     *
     * <p>A first-class outcome rather than a failure. In a ten-step run, per-step accuracy
     * compounds — 0.95^10 is about 0.60 — so the productive move is not chasing per-step accuracy
     * but changing the shape of the error. A run that is 90% right and 10% "I could not establish
     * this" is deployable; one that is 97% right and 3% confidently wrong is not, because the 3%
     * is indistinguishable from the 97% at the point of use.
     */
    ABSTAINED,

    /** Ran, produced something, and it is known to be incomplete. */
    DEGRADED,

    /** Could not run or threw. */
    FAILED,

    /** Never attempted, because a precondition did not hold. */
    SKIPPED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
