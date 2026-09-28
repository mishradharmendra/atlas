package com.atlas.skill.domain.model;

/**
 * What a step must be true of before it runs, and what must be true after.
 *
 * <p>A closed set rather than free-text assertions. Conditions are checked by the run engine, get
 * written into the trace, and are read months later by someone asking why a step was skipped;
 * free text satisfies none of those.
 */
public enum Condition {

    /** The principal's entitlement snapshot is present and unexpired. */
    ENTITLED,

    /** At least one passage was retrieved. */
    EVIDENCE_PRESENT,

    /** Every claim carries a citation resolving to a span. */
    CITATIONS_RESOLVE,

    /** No source in the working set is under embargo at the query's as-of. */
    NO_EMBARGOED_SOURCE,

    /** The prior step produced a conclusion rather than an abstention. */
    PRIOR_STEP_CONCLUDED,

    /** The run is inside all three budget ceilings. */
    WITHIN_BUDGET,

    /**
     * Sources disagree and the disagreement was surfaced rather than resolved silently.
     *
     * <p>A postcondition, not a precondition. It is the check that stops a synthesis step
     * averaging two contradictory figures into one plausible number with no indication that
     * anything was in conflict.
     */
    CONFLICTS_SURFACED
}
