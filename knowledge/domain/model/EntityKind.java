package com.atlas.knowledge.domain.model;

/** What kind of thing an entity is. Constrains which relationships are admissible. */
public enum EntityKind {
    COMPANY,
    PERSON,
    PRODUCT,
    SECTOR,
    PLACE,

    /**
     * A named group whose membership is defined by someone else's methodology — an index, a peer
     * set, a regulatory category.
     *
     * <p>Distinct from {@link #SECTOR} because membership is both time-varying and contested:
     * whether a company is "in" one is a claim with a source and a date, not a property.
     */
    CLASSIFICATION
}
