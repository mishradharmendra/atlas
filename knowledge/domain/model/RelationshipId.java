package com.atlas.knowledge.domain.model;

/** Identity of an extracted edge. */
public record RelationshipId(String value) {

    public RelationshipId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("relationship id must not be blank");
        }
    }

    public static RelationshipId of(String value) {
        return new RelationshipId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
