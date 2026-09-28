package com.atlas.entitlement.domain.model;

/**
 * The outcome of an entitlement check, carrying <em>why</em> rather than just yes/no.
 *
 * <p>The reason is not diagnostic detail — it changes what the user is told. A retrieval that
 * returns nothing because nothing exists is a final answer. A retrieval that returns nothing
 * because this principal lacks the licence is a sales conversation. Collapsing both into {@code
 * false} is what makes an agent assert that a document does not exist when it does.
 */
public enum AccessDecision {

    PERMITTED,

    /** An explicit deny matched. Deny always beats allow. */
    DENIED_BY_RULE,

    /** No grant covers this content. The common case for unlicensed sources. */
    DENIED_NOT_GRANTED,

    /**
     * A grant exists but is not yet in force at the query's as-of. Distinguished from
     * {@link #DENIED_NOT_GRANTED} because it is temporary and worth telling the user about:
     * the content becomes available on a known date.
     */
    DENIED_EMBARGOED;

    public boolean isPermitted() {
        return this == PERMITTED;
    }
}
