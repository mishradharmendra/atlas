package com.atlas.evaluation.api;

/** How much of the corpus a thorough answer has to consult. */
public enum BreadthSensitivity {

    /** One authoritative source settles it. More sources add nothing but cost. */
    SINGLE_SOURCE,

    /** Needs corroboration across independent sources before it can be relied on. */
    CORROBORATED,

    /**
     * Needs the spread of opinion, not a consensus.
     *
     * <p>The question is what the disagreement is, so an answer that averages the sources has
     * destroyed the thing being asked for.
     */
    EXHAUSTIVE
}
