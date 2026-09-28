package com.atlas.knowledge.api;

import java.util.OptionalDouble;

/**
 * What a source's extractions are worth, counted rather than asserted.
 *
 * <h2>Why these numbers and not an accuracy score</h2>
 *
 * <p>Nobody has ground truth for a supply-chain graph. What the platform does have is the rate at
 * which two independent extractors disagree about a source's text, and what happened when a person
 * settled those disagreements. That is a real quality signal obtained for free from work already
 * being done, and it is the number that decides which feed to stop paying for.
 *
 * <p>{@link #undecidableRate()} is the one people leave out, and it is the most interesting: it
 * separates "the models are bad at this source" from "this source is ambiguous". A vendor whose
 * disputes are mostly undecidable is not sending noise, it is sending prose that does not say what
 * a reader needs — a different conversation and a different remedy.
 */
public record SourceQuality(
        String sourceId,
        long edgesProposed,
        long disputesRaised,
        long disputesRefuted,
        long disputesConfirmed,
        long disputesUndecidable) {

    /**
     * Below this, a rate is noise presented as a measurement.
     *
     * <p>Twenty is not a statistical claim, it is a floor: one disagreement out of three edges is
     * a 33% disagreement rate on a dashboard, and someone will take it to a vendor meeting. Rates
     * are withheld rather than shown with a caveat, because the caveat is what gets dropped when
     * the number is pasted into a slide.
     */
    public static final long MIN_SAMPLE = 20;

    /** How often two independent extractors read this source's text differently. */
    public OptionalDouble disagreementRate() {
        return rate(disputesRaised, edgesProposed);
    }

    /** Of the disputes a person settled, how often the proposal was simply wrong. */
    public OptionalDouble refutedRate() {
        return rate(disputesRefuted, resolved());
    }

    /** Of the disputes a person settled, how often the source did not settle the question. */
    public OptionalDouble undecidableRate() {
        return rate(disputesUndecidable, resolved());
    }

    public long resolved() {
        return disputesRefuted + disputesConfirmed + disputesUndecidable;
    }

    private static OptionalDouble rate(long numerator, long denominator) {
        if (denominator < MIN_SAMPLE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) numerator / denominator);
    }
}
