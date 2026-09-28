package com.atlas.artifact.domain.model;

/** Identity of a deliverable. */
public record ArtifactId(String value) {

    public ArtifactId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("artifact id must not be blank");
        }
    }

    public static ArtifactId of(String value) {
        return new ArtifactId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
