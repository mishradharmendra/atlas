package com.atlas.evaluation.domain.model;

/**
 * The seven axes a professional answer is judged on.
 *
 * <p>These are a formalisation of professional judgement, not a decomposition an engineer derives
 * from first principles. They are fixed as an enum so that adding an eighth is a compile-time
 * event at every call site — including the frozen rubrics that would otherwise silently start
 * scoring out of a different total.
 */
public enum Axis {

    /** Did it state what the sources explicitly say. The axis everyone builds first. */
    EXPLICIT(false),

    /** Did it draw the inference a competent reader would draw, without overreaching. */
    IMPLICIT(false),

    /**
     * Is the evidence current enough for the question, and is its as-of stated.
     *
     * <p>A correct answer built on last year's filing is wrong in the way that matters most:
     * confidently, and in a form nobody re-checks.
     */
    TEMPORAL(true),

    /**
     * Does the evidence come from sources that carry weight for this claim.
     *
     * <p>A transcript and a blog post can support the same sentence. Only one of them survives
     * being cited in an investment committee.
     */
    AUTHORITY(true),

    /**
     * Does the evidence span the sources a thorough analyst would consult.
     *
     * <p>The axis that catches the failure retrieval metrics cannot see: three passages from one
     * document, read by an agent as three independent confirmations.
     */
    BREADTH(true),

    /** Does every claim resolve to a span that supports it. */
    REFERENCES(false),

    /** Does it reconcile what the sources disagree about, rather than averaging them. */
    SYNTHESIS(false);

    private final boolean loadBearing;

    Axis(boolean loadBearing) {
        this.loadBearing = loadBearing;
    }

    /**
     * Whether this axis counts toward the minimum share of positive weight a rubric must carry.
     *
     * <p>Temporal, authority and breadth. Left to drift, rubrics become exact-match checklists —
     * they are the easiest axis to write criteria for and the easiest to score — and the resulting
     * benchmark rewards a system that quotes accurately from one stale source.
     */
    public boolean isLoadBearing() {
        return loadBearing;
    }
}
