package com.atlas.prediction.domain.model;

import java.util.Collection;
import java.util.OptionalDouble;

/**
 * What a source's claims have been worth, counted from predictions the world has graded.
 *
 * <h2>Why this is a final class with a private constructor</h2>
 *
 * <p>So that a weight cannot be written down. It is the only defence that survives contact with
 * the first quarter in which an expensive vendor scores badly: at that point somebody will want to
 * adjust the number, the adjustment will be perfectly reasonable, and from then on the weights say
 * what the company wishes were true. {@code SourceWeightIsDerivedTest} fails the build if a public
 * constructor or a setter appears here, because the rule is only worth anything if breaking it is
 * noisy.
 *
 * <h2>How this differs from the knowledge module's SourceQuality</h2>
 *
 * <p>{@code SourceQuality} measures whether a source can be read — whether two extractors agree
 * about what its text says. This measures whether it was right. A meticulously written newsletter
 * scores well on the first and can score badly on the second, and it is the second that decides
 * whether to keep paying for it.
 */
public final class SourceWeight {

    /**
     * Below this, a score is noise with a decimal point.
     *
     * <p>Matched to {@code SourceQuality.MIN_SAMPLE} on purpose: two quality numbers about the
     * same source with different disclosure floors invite the reader to compare them, and the
     * comparison is wrong. Withheld rather than shown with a caveat, because the caveat is what
     * gets dropped when the figure is pasted into a vendor review.
     */
    public static final long MIN_SAMPLE = 20;

    private final String sourceId;
    private final long scored;
    private final long unresolvable;
    private final double summedBrier;

    private SourceWeight(String sourceId, long scored, long unresolvable, double summedBrier) {
        this.sourceId = sourceId;
        this.scored = scored;
        this.unresolvable = unresolvable;
        this.summedBrier = summedBrier;
    }

    /**
     * Derives a source's standing from the predictions that rested on it.
     *
     * <p>The only way to obtain one. Predictions still open contribute nothing — including them
     * as zeros would reward a source for claims that have not been tested yet, which is exactly
     * the period during which a new vendor looks best.
     */
    public static SourceWeight derivedFrom(String sourceId, Collection<Prediction> predictions) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("a weight must name its source");
        }
        long scored = 0;
        long unresolvable = 0;
        double summed = 0.0;

        for (Prediction prediction : predictions) {
            if (!prediction.sourceIds().contains(sourceId)) {
                continue;
            }
            if (prediction.status() == PredictionStatus.RESOLVED
                    && prediction.outcome() == Outcome.UNRESOLVABLE) {
                unresolvable++;
                continue;
            }
            OptionalDouble brier = prediction.brierScore();
            if (brier.isPresent()) {
                scored++;
                summed += brier.getAsDouble();
            }
        }
        return new SourceWeight(sourceId, scored, unresolvable, summed);
    }

    /**
     * Mean Brier score across this source's graded predictions. Lower is better.
     *
     * <p>Empty below the sample floor rather than defaulted. A default is a number, and a number
     * gets ranked.
     */
    public OptionalDouble meanBrier() {
        if (scored < MIN_SAMPLE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(summedBrier / scored);
    }

    /**
     * The share of this source's due predictions that could not be graded at all.
     *
     * <p>Reported beside the score because the two failure modes need different responses. A
     * source that is often wrong is a source to stop trusting; a source whose claims are mostly
     * unresolvable is usually a sign that the criteria written against it were vague, which is a
     * problem with us rather than with them.
     */
    public OptionalDouble unresolvableRate() {
        long total = scored + unresolvable;
        if (total < MIN_SAMPLE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) unresolvable / total);
    }

    public String sourceId() {
        return sourceId;
    }

    public long scored() {
        return scored;
    }

    public long unresolvable() {
        return unresolvable;
    }
}
