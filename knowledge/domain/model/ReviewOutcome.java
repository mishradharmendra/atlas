package com.atlas.knowledge.domain.model;

/** What a reviewer decided about a disputed edge. */
public enum ReviewOutcome {

    /**
     * The proposing extractor was right. The reviewer becomes the second, human extractor and the
     * edge is verified through the ordinary path.
     */
    CONFIRMED,

    /** The dissenting extractor was right. The edge is rejected. */
    REFUTED,

    /**
     * The source text does not settle it.
     *
     * <p>Kept distinct from {@link #REFUTED} because it says something different about the source:
     * the models were not wrong, the document was ambiguous. Folding it into a rejection would
     * blame the extractor for the corpus and distort every per-model quality figure derived from
     * this queue. The edge stays withheld.
     */
    UNDECIDABLE
}
