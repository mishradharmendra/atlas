package com.atlas.knowledge.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Two independent extractors agreeing on the same edge. The only key that unlocks a withheld edge.
 *
 * <h2>Why two, and why from different families</h2>
 *
 * <p>Errors made by one model are not random. A model that misreads "supplies components to" as
 * "is a subsidiary of" in a particular sentence structure will misread it the same way every time,
 * and so will another checkpoint of the same base model trained on the same data. Running the
 * extraction twice against {@code gpt-4o} and {@code gpt-4o-mini} therefore measures
 * <em>determinism</em>, not correctness, while producing a number that looks like corroboration.
 * That number is worse than no number: it converts an unverified edge into one the system reports
 * as verified.
 *
 * <p>Two different families do not decorrelate errors completely — shared pretraining data
 * guarantees some overlap — but they decorrelate the tokenisation, the instruction tuning and the
 * failure modes that come with them. That is the available improvement, and it is the reason the
 * check is on family rather than on model name.
 *
 * <p>A human reviewer is modelled as an extractor in the {@code human} family, so resolving a
 * review-queue item produces a verification through exactly this path rather than a back door.
 */
public record Verification(
        ExtractorId first, ExtractorId second, Instant at, String agreedStatement) {

    public Verification {
        Objects.requireNonNull(first, "first extractor");
        Objects.requireNonNull(second, "second extractor");
        Objects.requireNonNull(at, "verification instant");

        if (first.equals(second)) {
            throw new IllegalArgumentException(
                    "an extractor cannot verify itself: " + first + ". Re-running the same model "
                            + "measures sampling temperature, not truth");
        }
        if (first.sharesFamilyWith(second)) {
            throw new IllegalArgumentException(
                    ("%s and %s are the same model family. Their errors are correlated, so their "
                                    + "agreement is evidence of determinism rather than of "
                                    + "correctness")
                            .formatted(first, second));
        }
        if (agreedStatement == null || agreedStatement.isBlank()) {
            throw new IllegalArgumentException(
                    "a verification must record what the two extractors agreed on; 'they agreed' "
                            + "without the statement cannot be audited or re-judged");
        }
    }

    /** Whether a person was one of the two. Tracked because it is the expensive kind. */
    public boolean involvedHuman() {
        return ExtractorId.HUMAN_FAMILY.equals(first.family())
                || ExtractorId.HUMAN_FAMILY.equals(second.family());
    }
}
