package com.atlas.evaluation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One execution of a benchmark, against a frozen rubric and a pinned baseline.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>The rubric must be frozen.</b> A result graded by a draft is a result whose standard can
 *       still change afterwards, which makes the number unfalsifiable rather than merely
 *       provisional.
 *   <li><b>A baseline must be named.</b> A run with nothing to compare against reports a number,
 *       and a number without a reference point is a decoration.
 *   <li><b>Scored and unanswerable are counted separately.</b> A question with no relevant document
 *       has undefined recall, not zero. Folding them together understates every slice they touch,
 *       and the understatement is largest in exactly the slices deliberately seeded with them.
 * </ol>
 */
public record EvalRun(
        EvalRunId id,
        String benchmark,
        RubricId rubricId,
        int rubricVersion,
        BaselineId baselineId,
        Instant ranAt,
        String systemFingerprint,
        int questions,
        int scored,
        Map<String, Double> metrics,
        long costPerQuestionMicros) {

    public EvalRun {
        Objects.requireNonNull(id, "run id");
        Objects.requireNonNull(rubricId, "rubric id");
        Objects.requireNonNull(baselineId, "baseline id: a run with nothing to compare against "
                + "reports a number, and a number without a reference point is a decoration");
        Objects.requireNonNull(ranAt, "ran at");
        if (benchmark == null || benchmark.isBlank()) {
            throw new IllegalArgumentException("a run belongs to a named benchmark");
        }
        if (systemFingerprint == null || systemFingerprint.isBlank()) {
            // Model, prompt version, index version and retrieval params. Without it two runs of
            // different systems are indistinguishable in the history, and an A/B comparison shows
            // no delta because both arms read the same cache.
            throw new IllegalArgumentException(
                    "a run must record the fingerprint of the system that produced it");
        }
        if (questions < 1) {
            throw new IllegalArgumentException("a run with no questions measured nothing");
        }
        if (scored < 0 || scored > questions) {
            throw new IllegalArgumentException(
                    "scored (%d) must be between 0 and questions (%d)".formatted(scored, questions));
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("a run with no metrics measured nothing");
        }
        if (costPerQuestionMicros < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        metrics = Map.copyOf(metrics);
    }

    /**
     * Compares this run to a baseline, slice by slice.
     *
     * <p>Slice-wise rather than aggregate, because the aggregate is where the failure that matters
     * hides: a benchmark averaging 0.90 is entirely consistent with one query class having
     * collapsed to zero, and that class is somebody's whole job.
     *
     * @param tolerance how far a slice may fall before it counts as a regression. Not zero:
     *     judge sampling and tie-breaking move small slices by a point or two, and a gate that
     *     fires on noise is a gate that gets disabled.
     */
    public List<Regression> regressionsAgainst(Baseline baseline, double tolerance) {
        if (!baseline.id().equals(baselineId)) {
            throw new IllegalArgumentException(
                    "run %s is pinned to baseline %s, not %s"
                            .formatted(id, baselineId, baseline.id()));
        }
        if (baseline.rubricVersion() != rubricVersion) {
            // Two rubric versions grade differently by construction, so the difference between
            // the numbers is a change of yardstick wearing the costume of a quality delta.
            throw new IllegalArgumentException(
                    ("run graded by rubric v%d cannot be compared to a baseline graded by v%d; "
                                    + "re-baseline first")
                            .formatted(rubricVersion, baseline.rubricVersion()));
        }

        return baseline.metrics().entrySet().stream()
                .map(entry -> {
                    Double now = metrics.get(entry.getKey());
                    if (now == null) {
                        // A slice the baseline measured and this run did not. Silence here would
                        // let a regression be hidden by deleting the slice that shows it.
                        return new Regression(entry.getKey(), entry.getValue(), Double.NaN, true);
                    }
                    boolean regressed = now < entry.getValue() - tolerance;
                    return regressed
                            ? new Regression(entry.getKey(), entry.getValue(), now, false)
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Questions excluded from recall because they have no relevant document. */
    public int unanswerable() {
        return questions - scored;
    }

    /** A slice that fell, or vanished. */
    public record Regression(String slice, double baseline, double observed, boolean missing) {

        @Override
        public String toString() {
            return missing
                    ? "%s: measured by the baseline at %.4f, absent from this run".formatted(slice, baseline)
                    : "%s: %.4f -> %.4f".formatted(slice, baseline, observed);
        }
    }
}
