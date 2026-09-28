package com.atlas.evaluation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A frozen standard for what a good answer to one question looks like.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Immutable after {@link #freeze}.</b> No criterion may be added, edited, removed or
 *       reweighted. Enforced here rather than by review, because the alternative fails silently:
 *       editing a criterion redefines every number ever reported under this rubric's name, and no
 *       report will look wrong afterwards.
 *   <li><b>A rubric must carry weight on the axes that decay.</b> At least {@value
 *       #MIN_LOAD_BEARING_SHARE_PERCENT}% of positive weight sits on temporal, authority and
 *       breadth.
 *   <li><b>Frozen rubrics are versioned, not mutated.</b> Revising a standard produces a new
 *       version; the old one keeps grading the results already reported against it.
 * </ol>
 */
public class Rubric {

    /**
     * The floor on the share of positive weight carried by temporal, authority and breadth.
     *
     * <p>The target is around 40%. The floor sits below it deliberately: a hard bound at the target
     * would fail a rubric that lands at 39% for no principled reason, and a rule that fires on
     * defensible work gets relaxed rather than obeyed.
     *
     * <p>What it does catch is a rubric that has quietly become an exact-match checklist. Explicit
     * criteria are the easiest to write and the easiest to score, so every rubric drifts that way
     * under time pressure, and the resulting benchmark rewards a system that quotes accurately from
     * one stale source.
     */
    public static final int MIN_LOAD_BEARING_SHARE_PERCENT = 35;

    private final RubricId id;
    private final String question;
    private final Instant asOf;
    private final int version;

    private final List<RubricCriterion> criteria = new ArrayList<>();
    private RubricStatus status = RubricStatus.DRAFT;
    private Instant frozenAt;

    public Rubric(RubricId id, String question, Instant asOf, int version) {
        this.id = Objects.requireNonNull(id, "rubric id");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("a rubric grades a question; none was given");
        }
        this.question = question;
        this.asOf = Objects.requireNonNull(asOf, "as-of");
        if (version < 1) {
            throw new IllegalArgumentException("rubric version starts at 1, was " + version);
        }
        this.version = version;
    }

    // -- authoring ---------------------------------------------------------

    public void addCriterion(RubricCriterion criterion) {
        requireDraft("add a criterion to");
        if (criteria.stream().anyMatch(c -> c.id().equals(criterion.id()))) {
            throw new IllegalArgumentException("duplicate criterion id " + criterion.id());
        }
        criteria.add(criterion);
    }

    public void removeCriterion(String criterionId) {
        requireDraft("remove a criterion from");
        criteria.removeIf(c -> c.id().equals(criterionId));
    }

    /**
     * Closes the rubric to further change.
     *
     * @throws IllegalStateException if the rubric has no criteria, or too little of its positive
     *     weight sits on the axes that decay
     */
    public void freeze(Instant at) {
        requireDraft("freeze");
        if (criteria.isEmpty()) {
            throw new IllegalStateException("a rubric with no criteria grades nothing");
        }

        int share = loadBearingSharePercent();
        if (share < MIN_LOAD_BEARING_SHARE_PERCENT) {
            throw new IllegalStateException(
                    ("rubric %s puts %d%% of its positive weight on temporal, authority and "
                                    + "breadth; at least %d%% is required. Below that the benchmark "
                                    + "rewards a system that quotes accurately from one stale "
                                    + "source.")
                            .formatted(id, share, MIN_LOAD_BEARING_SHARE_PERCENT));
        }

        this.status = RubricStatus.FROZEN;
        this.frozenAt = Objects.requireNonNull(at, "frozen at");
    }

    /**
     * Produces the next version as a fresh draft.
     *
     * <p>Revision is a new version rather than an edit, so the results already reported against
     * this version keep the standard they were graded by. A benchmark whose history cannot be
     * re-derived is a benchmark whose trend line means nothing.
     */
    public Rubric revise() {
        Rubric next = new Rubric(id, question, asOf, version + 1);
        criteria.forEach(next.criteria::add);
        return next;
    }

    // -- queries -----------------------------------------------------------

    /** Share of total positive weight carried by temporal, authority and breadth. */
    public int loadBearingSharePercent() {
        int positive = criteria.stream().mapToInt(c -> c.weight().positiveContribution()).sum();
        if (positive == 0) {
            // Every criterion names a failure and none names a virtue. Nothing can score above
            // zero, so the share is undefined rather than complete.
            return 0;
        }
        int loadBearing = criteria.stream()
                .filter(c -> c.axis().isLoadBearing())
                .mapToInt(c -> c.weight().positiveContribution())
                .sum();
        return loadBearing * 100 / positive;
    }

    public boolean isFrozen() {
        return status == RubricStatus.FROZEN;
    }

    public List<RubricCriterion> criteria() {
        return List.copyOf(criteria);
    }

    public List<RubricCriterion> mandatoryCriteria() {
        return criteria.stream().filter(RubricCriterion::isMandatory).toList();
    }

    public RubricId id() {
        return id;
    }

    public String question() {
        return question;
    }

    public Instant asOf() {
        return asOf;
    }

    public int version() {
        return version;
    }

    public RubricStatus status() {
        return status;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    /** Restores persisted state. Not a lifecycle transition, so it runs no validation. */
    public void rehydrate(RubricStatus status, Instant frozenAt) {
        this.status = Objects.requireNonNull(status, "status");
        this.frozenAt = frozenAt;
    }

    private void requireDraft(String action) {
        if (status == RubricStatus.FROZEN) {
            throw new IllegalStateException(
                    ("cannot %s frozen rubric %s v%d: every number already reported under it was "
                                    + "produced by these criteria. Call revise() to author v%d.")
                            .formatted(action, id, version, version + 1));
        }
    }
}
