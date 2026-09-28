package com.atlas.memory.domain.model;

/**
 * What kind of claim a memory entry is, which decides how long it may be trusted.
 *
 * <p>Kept as a closed set rather than a free-text label because the expiry rule differs per kind
 * and a label nobody validates becomes "misc" within a month.
 */
public enum MemoryKind {

    /**
     * A figure or relationship a filing stated, carried forward with its source.
     *
     * <p>Lives as long as its source does, which is why {@code sourceDocIds} is mandatory here:
     * a remembered fact with no document behind it cannot be withdrawn when the document is.
     */
    ESTABLISHED_FACT,

    /** How this analyst likes an answer shaped. Long-lived and carries no claim about the world. */
    PREFERENCE,

    /**
     * What an earlier run concluded.
     *
     * <p>Expires by default. A conclusion is drawn from evidence available at a moment, and a
     * conclusion with no expiry silently becomes a claim about now.
     */
    PRIOR_CONCLUSION,

    /**
     * That something did not work — a step that failed, a source that had nothing.
     *
     * <p>Worth remembering precisely because the alternative is an agent rediscovering the same
     * dead end on every run and charging for it each time.
     */
    FAILURE;

    /** Whether an entry of this kind must name the documents it came from. */
    public boolean requiresSource() {
        return this == ESTABLISHED_FACT;
    }
}
