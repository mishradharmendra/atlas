package com.atlas.shared.provenance;

import java.time.Instant;

/**
 * A claim bound to the evidence that supports it.
 *
 * <p>Emitted with every factual assertion the platform makes. The quoted text is captured
 * <em>verbatim</em> and never paraphrased, because in this domain the signal is sometimes the
 * exact word a CFO chose: "supply-constrained" and "demand is healthy" carry different information
 * from any summary of either.
 *
 * <p>The publication date is carried alongside the tier rather than looked up later, so that
 * temporal reasoning ("is this still current?") can be done without a second fetch. Currency is
 * relative to the document type's shelf life — a news item goes stale in days, a 10-K in a
 * quarter — which is why a fresh tertiary source does not substitute for the latest filing.
 */
public record Citation(
        SpanRef span,
        String quotedText,
        AuthorityTier authority,
        Instant publishedAt,
        String sourceName) {

    /** Contractual quotation ceilings are far below anything fair-use would permit. */
    private static final int MAX_QUOTE_CHARS = 2_000;

    public Citation {
        if (span == null) {
            throw new IllegalArgumentException("citation must reference a span");
        }
        if (quotedText == null || quotedText.isBlank()) {
            throw new IllegalArgumentException(
                    "citation must quote the supporting text verbatim; a citation whose quote was "
                            + "lost cannot be verified by the citation-walk suite");
        }
        if (quotedText.length() > MAX_QUOTE_CHARS) {
            throw new IllegalArgumentException(
                    "quote of %d chars exceeds the %d-char contractual ceiling"
                            .formatted(quotedText.length(), MAX_QUOTE_CHARS));
        }
        if (authority == null) {
            throw new IllegalArgumentException("citation must carry an authority tier");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException(
                    "citation must carry a publication date; undated evidence cannot be scored on "
                            + "the temporal axis and is indistinguishable from stale evidence");
        }
    }

    /** Whether this evidence predates a cutoff — the basis of "only what landed since". */
    public boolean predates(Instant cutoff) {
        return publishedAt.isBefore(cutoff);
    }
}
