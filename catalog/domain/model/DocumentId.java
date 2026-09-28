package com.atlas.catalog.domain.model;

/** Identity of a catalogued document. Stable across re-parsing and re-indexing. */
public record DocumentId(String value) {

    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("document id must not be blank");
        }
    }

    public static DocumentId of(String value) {
        return new DocumentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
