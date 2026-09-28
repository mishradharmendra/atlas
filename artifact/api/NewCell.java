package com.atlas.artifact.api;

import java.time.Instant;

/**
 * One value to bind into a new deliverable, with the evidence behind it.
 *
 * <p>This is the caller's half of the bargain and the half it is entitled to supply: the agent
 * plane ran the retrieval and holds the spans, so it is the only party that knows what the number
 * is and where it was read. What the caller does <em>not</em> supply is the artifact's identity or
 * its status — those are minted and governed here.
 *
 * <p>The evidence fields are flattened rather than carrying a {@code Citation}, because this
 * crosses a module boundary and eventually a process one; the shape a caller has to construct
 * should not be a domain type whose constructor rules can change underneath it. The rules still
 * apply — {@link #toCell()} builds the real thing and fails the same way — but they are applied
 * at the boundary instead of being a compile-time dependency of every caller.
 */
public record NewCell(
        String cellRef,
        String value,
        String spanId,
        String docId,
        int page,
        int charStart,
        int charEnd,
        String quotedText,
        String authority,
        Instant publishedAt,
        String sourceName,
        String derivedFrom) {

    /** A cell read from a cited span. */
    public static NewCell extracted(
            String cellRef,
            String value,
            String spanId,
            String docId,
            int page,
            int charStart,
            int charEnd,
            String quotedText,
            String authority,
            Instant publishedAt,
            String sourceName) {
        return new NewCell(
                cellRef,
                value,
                spanId,
                docId,
                page,
                charStart,
                charEnd,
                quotedText,
                authority,
                publishedAt,
                sourceName,
                null);
    }

    /** A cell computed from other cells, which carry the citations. */
    public static NewCell derived(String cellRef, String value, String formula) {
        return new NewCell(cellRef, value, null, null, 0, 0, 0, null, null, null, null, formula);
    }

    public boolean isDerived() {
        return derivedFrom != null && !derivedFrom.isBlank();
    }
}
