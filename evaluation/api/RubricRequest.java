package com.atlas.evaluation.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Everything a rubric author is permitted to see about a question.
 *
 * <p>Four fields, and the list is the enforcement. There is no system output here, no candidate
 * answer, no retrieved evidence, no run id that could be used to fetch any of them — and the type
 * is a record, so there is no way to add one without a code review that is visibly about exactly
 * this.
 *
 * <p>The rule matters because the failure is invisible. A rubric written with the answer in view
 * grades the answer's shape: it rewards the structure the system already produces, marks the
 * omissions the system already makes as acceptable, and scores well. Every subsequent release is
 * then measured against a standard derived from the release before it, and the benchmark
 * ratchets toward whatever the system does rather than toward what the question requires.
 *
 * <p>Stated as a convention, this survives until the first sprint where writing rubrics is slow
 * and there is an obvious shortcut. Stated as a type, it does not come up.
 *
 * @param asOf what the question is being asked about. Distinct from when the rubric is written: a
 *     question about last quarter has different currency requirements from the same question
 *     asked today, and the rubric has to grade against the first.
 */
public record RubricRequest(
        String question,
        Instant asOf,
        TimeSensitivity timeSensitivity,
        BreadthSensitivity breadthSensitivity) {

    public RubricRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("a rubric grades a question; none was given");
        }
        Objects.requireNonNull(asOf, "as-of");
        Objects.requireNonNull(timeSensitivity, "time sensitivity");
        Objects.requireNonNull(breadthSensitivity, "breadth sensitivity");
    }
}
