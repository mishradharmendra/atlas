package com.atlas.catalog.domain.model;

/** Where a document sits in its lifecycle. */
public enum DocumentStatus {

    /** Live and searchable. */
    PUBLISHED,

    /**
     * Replaced by a later document — a restated filing, an amended note.
     *
     * <p>Still searchable, deliberately. An analyst asking what was reported at the time needs the
     * original, and a restatement is itself a signal worth surfacing. What must not happen is
     * answering a current-state question from superseded content without saying so, which is why a
     * superseded document always names its successor.
     */
    SUPERSEDED,

    /**
     * Withdrawn by the publisher, or removed under a takedown obligation.
     *
     * <p>Terminal. Must disappear from every index, from graph edges derived from it, from cached
     * agent memory, and be flagged on deliverables that cited it.
     */
    RETRACTED
}
