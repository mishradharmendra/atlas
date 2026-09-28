package com.atlas.skill.domain.model;

/**
 * What the run engine does when a step fails or its postcondition does not hold.
 *
 * <p>Declared per step rather than handled by a catch block, because the right answer differs by
 * step and the person who knows which is right is the one authoring the skill, not the one who
 * wrote the engine.
 */
public enum OnFail {

    /**
     * Stop the run. Nothing downstream can be trusted.
     *
     * <p>For steps whose output everything else rests on — entitlement resolution, the retrieval
     * that supplies all the evidence.
     */
    ABORT,

    /**
     * Record an abstention and carry on.
     *
     * <p>The step could not establish its conclusion and says so. Downstream steps see the
     * abstention and, if the step was mandatory, the run may not emit an artefact.
     */
    ABSTAIN,

    /**
     * Try again, up to the step's attempt limit, then abstain.
     *
     * <p>Only sound for steps that are genuinely idempotent. Retrying a step that has already
     * written something produces the duplicate that downstream code counts as corroboration.
     */
    RETRY,

    /**
     * Continue with a reduced result, marked as degraded.
     *
     * <p>The dangerous one, and deliberately named so that choosing it is a decision. A degraded
     * result that is not marked is indistinguishable from a complete one at the point of use.
     */
    CONTINUE_DEGRADED
}
