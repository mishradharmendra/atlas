package com.atlas.knowledge.domain.model;

/** Identity of a review-queue item. */
public record ReviewItemId(String value) {

    public ReviewItemId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("review item id must not be blank");
        }
    }

    public static ReviewItemId of(String value) {
        return new ReviewItemId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
