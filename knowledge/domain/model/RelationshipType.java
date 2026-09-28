package com.atlas.knowledge.domain.model;

/**
 * The predicate of an edge.
 *
 * <p>A closed set rather than free text. Free-text predicates are what turn a graph back into a
 * bag of sentences: "supplies", "is a supplier to" and "provides components for" become three
 * unjoinable edges, and multi-hop traversal — the only reason to build a graph — stops working.
 */
public enum RelationshipType {
    SUPPLIES,
    CUSTOMER_OF,
    COMPETES_WITH,
    SUBSIDIARY_OF,
    EXECUTIVE_OF,
    INVESTS_IN,
    PARTNERS_WITH,
    MEMBER_OF;

    /**
     * Whether the edge means the same thing read backwards.
     *
     * <p>Traversal needs this. {@code COMPETES_WITH} is mutual, so a query for A's competitors must
     * find an edge stored as B→A; {@code SUPPLIES} is not, and treating it as mutual would invert
     * a supply chain.
     */
    public boolean isSymmetric() {
        return this == COMPETES_WITH || this == PARTNERS_WITH;
    }
}
