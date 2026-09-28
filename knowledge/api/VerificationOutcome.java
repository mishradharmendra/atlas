package com.atlas.knowledge.api;

/**
 * What happened when a second extractor was asked about a proposed edge.
 *
 * <p>{@code DISPUTED} is not a failure. It is the pipeline working: the edge stays invisible and a
 * labelled hard case lands in the review queue.
 */
public sealed interface VerificationOutcome {

    /** The two extractors agreed. The edge is now traversable. */
    record Verified(String relationshipId, String firstExtractor, String secondExtractor)
            implements VerificationOutcome {}

    /** They disagreed. The edge stays withheld and a review item was raised. */
    record Disputed(String relationshipId, String reviewItemId, String disagreement)
            implements VerificationOutcome {}
}
