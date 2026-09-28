package com.atlas.knowledge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA row for a disagreement awaiting review. */
@Entity
@Table(name = "knowledge_review_item")
class ReviewItemRow {

    @Id
    @Column(name = "review_item_id", nullable = false, length = 128)
    String reviewItemId;

    @Column(name = "relationship_id", nullable = false, length = 128)
    String relationshipId;

    @Column(name = "proposed_model", nullable = false, length = 128)
    String proposedModel;

    @Column(name = "proposed_family", nullable = false, length = 64)
    String proposedFamily;

    @Column(name = "dissent_model", nullable = false, length = 128)
    String dissentModel;

    @Column(name = "dissent_family", nullable = false, length = 64)
    String dissentFamily;

    @Column(name = "disagreement", nullable = false, length = 2048)
    String disagreement;

    @Column(name = "source_id", nullable = false, length = 128)
    String sourceId;

    @Column(name = "raised_at", nullable = false)
    Instant raisedAt;

    @Column(name = "state", nullable = false, length = 16)
    String state;

    @Column(name = "claimed_by", length = 128)
    String claimedBy;

    @Column(name = "outcome", length = 16)
    String outcome;

    @Column(name = "resolved_by", length = 128)
    String resolvedBy;

    @Column(name = "resolved_at")
    Instant resolvedAt;

    protected ReviewItemRow() {
        // required by JPA
    }
}
