package com.atlas.knowledge.domain.model;

/** Identity of a canonical entity. Stable across every name the entity has ever traded under. */
public record EntityId(String value) {

    public EntityId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("entity id must not be blank");
        }
    }

    public static EntityId of(String value) {
        return new EntityId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
