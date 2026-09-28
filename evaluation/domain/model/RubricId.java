package com.atlas.evaluation.domain.model;

/** Identity of a rubric. Stable across versions: v1 and v2 grade the same question. */
public record RubricId(String value) {

    public RubricId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rubric id must not be blank");
        }
    }

    public static RubricId of(String value) {
        return new RubricId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
