package com.atlas.evaluation.domain.model;

/** Identity of an evaluation run. */
public record EvalRunId(String value) {

    public EvalRunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EvalRunId must not be blank");
        }
    }

    public static EvalRunId of(String value) {
        return new EvalRunId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
