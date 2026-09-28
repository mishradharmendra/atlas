package com.atlas.shared.outcome;

/** Why a step declined to assert a conclusion. */
public enum AbstentionReason {

    /**
     * Nothing in the corpus matches the constraints — a future quarter's results, a metric the
     * company does not disclose. A legitimate final answer, not a failure.
     */
    NO_EVIDENCE_EXISTS,

    /**
     * Matching evidence exists but this principal may not see it.
     *
     * <p>Must never be collapsed into {@link #NO_EVIDENCE_EXISTS}. Doing so makes the platform
     * state as fact that something does not exist when it does, which is a worse failure than
     * refusing to answer.
     */
    NOT_PERMITTED,

    /** Evidence was found but too little of the answerable set to support a conclusion. */
    INSUFFICIENT_COVERAGE,

    /** Authoritative sources disagree and the conflict is material. Surfaced, not resolved. */
    CONFLICTING_EVIDENCE,

    /** The independent verifier did not reconcile the extraction with the source. */
    VERIFICATION_FAILED,

    /** Operational, not epistemic. The run hit its token, time or spend ceiling. */
    BUDGET_EXCEEDED
}
