package com.atlas.prediction.domain.model;

import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A claim about the future, stated so that the world can prove it wrong.
 *
 * <p>The constructor is where this module does its work. Every rule below rejects a prediction
 * that would otherwise be unscoreable, and an unscoreable prediction is worse than none: it takes
 * up space on the record while being incapable of ever counting against anybody.
 */
public class Prediction {

    private final PredictionId id;
    private final TenantId tenant;
    private final String claim;
    private final String resolutionCriterion;
    private final double confidence;
    private final Instant madeAt;
    private final Instant resolvesAt;
    private final List<String> sourceIds;
    private final String artifactId;

    private PredictionStatus status = PredictionStatus.OPEN;
    private Outcome outcome;
    private Instant resolvedAt;
    private String resolutionNote;

    public Prediction(
            PredictionId id,
            TenantId tenant,
            String claim,
            String resolutionCriterion,
            double confidence,
            Instant madeAt,
            Instant resolvesAt,
            List<String> sourceIds,
            String artifactId) {

        this.id = Objects.requireNonNull(id, "prediction id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.madeAt = Objects.requireNonNull(madeAt, "made-at");
        this.resolvesAt = Objects.requireNonNull(resolvesAt, "resolves-at");

        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("a prediction must state a claim");
        }

        // The rule the module exists for. Without a criterion the prediction is graded by
        // whoever reads it later, against whatever they remember having expected.
        if (resolutionCriterion == null || resolutionCriterion.isBlank()) {
            throw new IllegalArgumentException(
                    ("prediction '%s' has no resolution criterion. A claim that cannot be shown "
                                    + "false will never be counted wrong, and it improves the "
                                    + "record by dropping out of the denominator")
                            .formatted(claim));
        }

        // A prediction whose date has already passed is a retrodiction, and it is written by
        // someone who already knows the answer. It would score well and mean nothing.
        if (!resolvesAt.isAfter(madeAt)) {
            throw new IllegalArgumentException(
                    ("prediction '%s' resolves at %s, not after it was made at %s. A claim about "
                                    + "the past is graded by an author who already knows how it "
                                    + "turned out")
                            .formatted(claim, resolvesAt, madeAt));
        }

        // Certainty is excluded at both ends. A stated 0 or 1 cannot be moved by evidence, which
        // is the only reason for writing a confidence down, and it makes the log-scoring rule
        // infinite rather than merely bad.
        if (!(confidence > 0.0 && confidence < 1.0)) {
            throw new IllegalArgumentException(
                    ("prediction '%s' is stated at confidence %s. Certainty cannot be updated by "
                                    + "what happens, so it is an assertion rather than a forecast")
                            .formatted(claim, confidence));
        }

        // A prediction resting on nothing cannot feed back to any source, which is the entire
        // purpose of recording it. It would be graded and the grade would go nowhere.
        if (sourceIds == null || sourceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    ("prediction '%s' names no sources. Its outcome could never be attributed, so "
                                    + "the feedback loop this module exists to close would not "
                                    + "close for it")
                            .formatted(claim));
        }

        this.claim = claim;
        this.resolutionCriterion = resolutionCriterion;
        this.confidence = confidence;
        this.sourceIds = List.copyOf(sourceIds);
        this.artifactId = artifactId;
    }

    /**
     * Grades the prediction against its criterion.
     *
     * <p>Refuses before the resolution date. Grading early means choosing the moment at which to
     * measure, and the moment will be chosen when the answer looks good — a forecast marked
     * correct in week three of a twelve-week window is not a correct forecast, it is a well-timed
     * one. The date was fixed in advance precisely so that this is not a decision.
     *
     * <p>Refuses a second grading. An outcome that can be revised is one that will be revised by
     * whoever is unhappy with the scoreboard, and the revision always runs in the same direction.
     */
    public void resolve(Outcome graded, Instant observedAt, String note) {
        Objects.requireNonNull(graded, "outcome");
        Objects.requireNonNull(observedAt, "observed-at");

        if (status == PredictionStatus.RESOLVED) {
            throw new IllegalStateException(
                    ("prediction %s is already resolved as %s. Regrading a settled forecast is how "
                                    + "a record improves without anything having improved")
                            .formatted(id, outcome));
        }
        if (observedAt.isBefore(resolvesAt)) {
            throw new IllegalArgumentException(
                    ("prediction %s resolves at %s and was graded at %s. Grading early picks the "
                                    + "moment of measurement, and the moment gets picked when the "
                                    + "answer is flattering")
                            .formatted(id, resolvesAt, observedAt));
        }
        if (graded == Outcome.UNRESOLVABLE && (note == null || note.isBlank())) {
            throw new IllegalArgumentException(
                    ("prediction %s was marked unresolvable with no reason. Unresolvable is the "
                                    + "one outcome that costs nobody anything, so it is the one "
                                    + "that needs saying why")
                            .formatted(id));
        }

        this.outcome = graded;
        this.resolvedAt = observedAt;
        this.resolutionNote = note;
        this.status = PredictionStatus.RESOLVED;
    }

    /**
     * Marks an ungraded prediction as overdue.
     *
     * <p>Not a failure and not an outcome — it contributes to no score. It exists so the count of
     * predictions nobody graded is visible next to the ones they did, because a forecast record
     * built only from graded predictions is a record of the ones somebody wanted to grade.
     */
    public void markOverdue(Instant now) {
        if (status == PredictionStatus.RESOLVED) {
            return;
        }
        if (now.isBefore(resolvesAt)) {
            throw new IllegalArgumentException(
                    "prediction %s is not due until %s".formatted(id, resolvesAt));
        }
        this.status = PredictionStatus.OVERDUE;
    }

    /**
     * The Brier score for this prediction: the squared distance between what was claimed and what
     * happened. Lower is better; 0.25 is what stating 0.5 about everything gets you.
     *
     * <p>A squared error rather than a hit rate, because a hit rate cannot tell apart a forecaster
     * who says 0.55 and is right 55% of the time — which is excellent — from one who says 0.95 and
     * is right 55% of the time, which is useless in the way that matters most. Confidence is only
     * worth recording if being confidently wrong costs more than being tentatively wrong.
     *
     * <p>Empty when unresolved or unresolvable: those are not zeros, and a zero here is a perfect
     * score.
     */
    public OptionalDouble brierScore() {
        if (status != PredictionStatus.RESOLVED || !outcome.isDecidable()) {
            return OptionalDouble.empty();
        }
        double actual = outcome == Outcome.CORRECT ? 1.0 : 0.0;
        double error = confidence - actual;
        return OptionalDouble.of(error * error);
    }

    public PredictionId id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public String claim() {
        return claim;
    }

    public String resolutionCriterion() {
        return resolutionCriterion;
    }

    public double confidence() {
        return confidence;
    }

    public Instant madeAt() {
        return madeAt;
    }

    public Instant resolvesAt() {
        return resolvesAt;
    }

    public List<String> sourceIds() {
        return sourceIds;
    }

    public String artifactId() {
        return artifactId;
    }

    public PredictionStatus status() {
        return status;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public String resolutionNote() {
        return resolutionNote;
    }

    /** Reconstitutes from storage without replaying the rules a resolution would apply. */
    public void rehydrate(
            PredictionStatus storedStatus,
            Outcome storedOutcome,
            Instant storedResolvedAt,
            String storedNote) {
        this.status = storedStatus;
        this.outcome = storedOutcome;
        this.resolvedAt = storedResolvedAt;
        this.resolutionNote = storedNote;
    }
}
