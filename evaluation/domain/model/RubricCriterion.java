package com.atlas.evaluation.domain.model;

import java.util.Objects;

/**
 * One thing a grader checks, on one axis, worth one weight.
 *
 * @param statement written as a checkable assertion about the answer, not as a quality adjective.
 *     "Names the fiscal period the figure belongs to" can be graded consistently by two people;
 *     "demonstrates good temporal awareness" cannot, and a rubric full of the second kind produces
 *     scores that move when the grader changes.
 */
public record RubricCriterion(String id, Axis axis, String statement, Weight weight) {

    public RubricCriterion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("criterion id must not be blank");
        }
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("criterion " + id + " must state what it checks");
        }
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(weight, "weight");
    }

    public boolean isMandatory() {
        return weight.isMandatory();
    }
}
