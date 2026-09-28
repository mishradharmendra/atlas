package com.atlas.shared.outcome;

/** The nature of a disagreement between sources. */
public enum ConflictKind {

    /** Two sources report different values for the same metric, period and basis. */
    VALUE_DISAGREEMENT,

    /** A restatement replaces an earlier figure. Mechanically resolvable in favour of the later. */
    SUPERSESSION,

    /**
     * The values differ because they are not measuring the same thing — GAAP against adjusted,
     * organic against reported. Not a disagreement at all, and reporting it as one is its own bug.
     */
    BASIS_MISMATCH,

    /** Sources agree on magnitude but disagree on direction or interpretation. */
    DIRECTIONAL
}
