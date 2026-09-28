package com.atlas.knowledge.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An extraction two models disagreed about, held for a person to settle.
 *
 * <h2>Why this is an aggregate and not a log line</h2>
 *
 * <p>A disagreement is the most informative event the extraction pipeline produces. It marks
 * exactly the sentences that are hard, it is the only unbiased sample of where the models fail,
 * and settling it yields a labelled example that is worth more than a hundred easy ones.
 *
 * <p>Written to a log it becomes a number on a dashboard that nobody acts on, and the label is
 * never captured. Modelled here it has a claimant, an outcome and a resolver identity — which
 * means the resolution can be fed back through {@link Verification} as a human extractor, and
 * "what fraction of this source's edges needed a human?" becomes answerable per source.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Resolution requires a claim.</b> Two reviewers settling the same item independently is
 *       how a queue silently produces contradictory labels.
 *   <li><b>Resolution is terminal and names the person.</b> An anonymous resolution cannot be
 *       audited, and a reversible one is not a decision.
 * </ol>
 */
public class ReviewItem {

    private final ReviewItemId id;
    private final RelationshipId edge;
    private final ExtractorId proposedBy;
    private final ExtractorId dissentedBy;
    private final String disagreement;
    private final String sourceId;
    private final Instant raisedAt;

    private ReviewState state;
    private String claimedBy;
    private ReviewOutcome outcome;
    private String resolvedBy;
    private Instant resolvedAt;

    public ReviewItem(
            ReviewItemId id,
            RelationshipId edge,
            ExtractorId proposedBy,
            ExtractorId dissentedBy,
            String disagreement,
            String sourceId,
            Instant raisedAt) {
        this.id = Objects.requireNonNull(id, "review item id");
        this.edge = Objects.requireNonNull(edge, "edge under review");
        this.proposedBy = Objects.requireNonNull(proposedBy, "proposing extractor");
        this.dissentedBy = Objects.requireNonNull(dissentedBy, "dissenting extractor");
        this.raisedAt = Objects.requireNonNull(raisedAt, "raised-at");

        if (proposedBy.equals(dissentedBy)) {
            throw new IllegalArgumentException(
                    "a disagreement needs two different extractors; " + proposedBy
                            + " disagreeing with itself is sampling noise, not a dispute");
        }
        if (disagreement == null || disagreement.isBlank()) {
            throw new IllegalArgumentException(
                    "a review item must state what the disagreement was; a queue of items reading "
                            + "'models disagreed' cannot be triaged or sampled");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "a review item must name the source; per-source disagreement rate is the point");
        }
        this.disagreement = disagreement;
        this.sourceId = sourceId;
        this.state = ReviewState.PENDING;
    }

    /** Takes the item out of the pool so two reviewers cannot settle it in different directions. */
    public void claim(String reviewer) {
        requireText(reviewer, "reviewer");
        if (state != ReviewState.PENDING) {
            throw new IllegalStateException(
                    "review item %s is %s and cannot be claimed".formatted(id, state));
        }
        this.state = ReviewState.CLAIMED;
        this.claimedBy = reviewer;
    }

    /**
     * Settles the item.
     *
     * <p>Returns the outcome rather than applying it. The edge is a separate aggregate and a
     * single method that mutated both would make the transaction boundary implicit; the
     * application service applies the outcome and both changes commit together or not at all.
     */
    public ReviewOutcome resolve(ReviewOutcome decision, String reviewer, Instant at) {
        Objects.requireNonNull(decision, "decision");
        requireText(reviewer, "reviewer");
        if (state != ReviewState.CLAIMED) {
            throw new IllegalStateException(
                    ("review item %s is %s; it must be claimed before it is resolved, otherwise "
                                    + "two reviewers can settle it in opposite directions")
                            .formatted(id, state));
        }
        if (!reviewer.equals(claimedBy)) {
            throw new IllegalStateException(
                    "review item %s is claimed by %s and cannot be resolved by %s"
                            .formatted(id, claimedBy, reviewer));
        }
        this.state = ReviewState.RESOLVED;
        this.outcome = decision;
        this.resolvedBy = reviewer;
        this.resolvedAt = at;
        return decision;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    /** Restores persisted state without replaying claim and resolution. */
    public void rehydrate(
            ReviewState state,
            String claimedBy,
            ReviewOutcome outcome,
            String resolvedBy,
            Instant resolvedAt) {
        this.state = Objects.requireNonNull(state, "state");
        this.claimedBy = claimedBy;
        this.outcome = outcome;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public ReviewItemId id() {
        return id;
    }

    public RelationshipId edge() {
        return edge;
    }

    public ExtractorId proposedBy() {
        return proposedBy;
    }

    public ExtractorId dissentedBy() {
        return dissentedBy;
    }

    public String disagreement() {
        return disagreement;
    }

    public String sourceId() {
        return sourceId;
    }

    public Instant raisedAt() {
        return raisedAt;
    }

    public ReviewState state() {
        return state;
    }

    public String claimedBy() {
        return claimedBy;
    }

    public ReviewOutcome outcome() {
        return outcome;
    }

    public String resolvedBy() {
        return resolvedBy;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }
}
