package com.atlas.skill.domain.model;

import java.util.Objects;

/**
 * How a step's output is judged, declared at authoring time.
 *
 * <p>Mandatory on every step. A step with no declared feedback cannot be improved: there is no
 * signal to optimise, and no way to distinguish a regression from a change. Requiring it here is
 * what stops a skill accreting unmeasured steps that nobody dares to delete because nobody can
 * show they do nothing.
 *
 * <p>{@code rubricId} points into the evaluation plane rather than duplicating criteria. The two
 * planes stay separate — a skill declares <em>which</em> standard applies, and the evaluation
 * context owns what that standard says and whether it is frozen.
 */
public record FeedbackSpec(String rubricId, FeedbackKind kind, boolean blocking) {

    public FeedbackSpec {
        if (rubricId == null || rubricId.isBlank()) {
            throw new IllegalArgumentException(
                    "a step must name the rubric it is judged by; a step with no feedback cannot "
                            + "be improved and cannot be shown to have regressed");
        }
        Objects.requireNonNull(kind, "feedback kind");
    }

    /** Judged, and a failing judgement stops the run rather than being reported afterwards. */
    public static FeedbackSpec blocking(String rubricId, FeedbackKind kind) {
        return new FeedbackSpec(rubricId, kind, true);
    }

    /** Judged and recorded, but the run proceeds. Most steps are this. */
    public static FeedbackSpec observed(String rubricId, FeedbackKind kind) {
        return new FeedbackSpec(rubricId, kind, false);
    }
}
