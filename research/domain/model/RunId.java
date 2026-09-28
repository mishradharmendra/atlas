package com.atlas.research.domain.model;

/** Identity of a research run. */
public record RunId(String value) {

    public RunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("run id must not be blank");
        }
    }

    public static RunId of(String value) {
        return new RunId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
