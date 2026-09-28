package com.atlas.evaluation.domain.model;

/**
 * Which half of the system a verdict is about.
 *
 * <p>Two judges against two rubrics, because a single quality number cannot be acted on. When it
 * falls, the question is whether retrieval stopped finding the evidence or synthesis stopped using
 * it — and those are different teams, different fixes, and different weeks of work.
 */
public enum JudgeKind {

    /** Grades the retrieved evidence alone, without seeing an answer. */
    EVIDENCE,

    /** Grades the answer given the evidence that was actually retrieved. */
    ANSWER
}
