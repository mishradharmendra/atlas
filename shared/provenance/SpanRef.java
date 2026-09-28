package com.atlas.shared.provenance;

/**
 * The atom of provenance: a precise, addressable region of a source document.
 *
 * <h2>Why the coordinates are mandatory</h2>
 *
 * <p>Page and character offsets are captured at parse time and carried unchanged through chunking,
 * embedding, fact extraction, graph edges, agent steps and into the final sentence of a memo or
 * the value in a spreadsheet cell. Every one of those hops must be reversible.
 *
 * <p>This is not a nicety. "Click the citation and land on the exact sentence" is the platform's
 * central promise, and it is also the mechanism by which a retracted document is cascaded out of
 * derived edges and previously generated artefacts. If the chain is lossy at any hop, both
 * capabilities are gone, and neither can be retrofitted once a corpus has been indexed — the
 * coordinates were never recorded, so there is nothing to recover.
 */
public record SpanRef(
        String spanId, String docId, int page, BoundingBox bbox, int charStart, int charEnd) {

    public SpanRef {
        if (spanId == null || spanId.isBlank()) {
            throw new IllegalArgumentException("span id must not be blank");
        }
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("doc id must not be blank");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was " + page);
        }
        if (charStart < 0 || charEnd < charStart) {
            throw new IllegalArgumentException(
                    "invalid char range [%d, %d)".formatted(charStart, charEnd));
        }
    }

    public int length() {
        return charEnd - charStart;
    }
}
