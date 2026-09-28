package com.atlas.skill.domain.model;

/** What kind of signal a step's feedback produces. */
public enum FeedbackKind {

    /** A judge scores the step's output against a frozen rubric. */
    RUBRIC_SCORE,

    /** A person accepted or corrected it. Expensive, scarce, and worth more than the rest. */
    HUMAN_REVIEW,

    /**
     * The step's claim was checked against the source it cited.
     *
     * <p>Cheap, automatic, and catches the failure that matters most in this domain: a number
     * that is plausible, confidently stated, and not in the document it points at.
     */
    CITATION_WALK,

    /**
     * Whether the step's abstention was correct.
     *
     * <p>Scored separately from quality because they move in opposite directions. A system
     * optimised only on quality learns to answer everything, and a system optimised only on
     * abstention learns to answer nothing.
     */
    ABSTENTION_CORRECTNESS
}
