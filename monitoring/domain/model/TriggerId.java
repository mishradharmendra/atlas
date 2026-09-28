package com.atlas.monitoring.domain.model;

/** Identity of a trigger. */
public record TriggerId(String value) {

    public TriggerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("trigger id must not be blank");
        }
    }

    public static TriggerId of(String value) {
        return new TriggerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
