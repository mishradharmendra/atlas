package com.atlas.evaluation.domain.model;

/** Identity of a pinned baseline. */
public record BaselineId(String value) {

    public BaselineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BaselineId must not be blank");
        }
    }

    public static BaselineId of(String value) {
        return new BaselineId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
