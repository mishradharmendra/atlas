package com.atlas.knowledge.domain.model;

/** Lifecycle of an extracted edge. */
public enum EdgeStatus {

    /**
     * Extracted but not corroborated. Stored, reviewable, and invisible to traversal.
     *
     * <p>The default. An edge arrives here and the only way out is {@link #ACTIVE} via a
     * {@link Verification}, or one of the two terminal states.
     */
    WITHHELD,

    /**
     * Corroborated by two independent extractors. The only status a traversal returns <em>today</em>.
     *
     * <p>Not the only status a traversal can ever return: a query asked about an earlier system
     * time legitimately sees an edge that has since been retracted, because it was believed then.
     */
    ACTIVE,

    /** Reviewed and found wrong. Terminal, and kept — a rejected edge is training signal. */
    REJECTED,

    /**
     * The document it was extracted from was withdrawn. Terminal.
     *
     * <p>Distinct from {@link #REJECTED} because the edge may well have been correct; what changed
     * is that the platform no longer holds the right to assert it. Collapsing the two would lose
     * the ability to answer "was this wrong, or did we lose the source?" — which are different
     * conversations with a publisher and with a client.
     */
    RETRACTED;

    public boolean isTerminal() {
        return this == REJECTED || this == RETRACTED;
    }
}
