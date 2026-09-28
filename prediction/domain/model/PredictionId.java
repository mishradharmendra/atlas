package com.atlas.prediction.domain.model;

/** Identity of a recorded prediction. */
public record PredictionId(String value) {

    public PredictionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("prediction id must not be blank");
        }
    }

    public static PredictionId of(String value) {
        return new PredictionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
