package com.atlas.knowledge.adapter.out.persistence;

import com.atlas.knowledge.domain.model.ExtractorId;
import com.atlas.knowledge.domain.model.RelationshipId;
import com.atlas.knowledge.domain.model.ReviewItem;
import com.atlas.knowledge.domain.model.ReviewItemId;
import com.atlas.knowledge.domain.model.ReviewOutcome;
import com.atlas.knowledge.domain.model.ReviewState;
import com.atlas.knowledge.domain.port.ReviewQueue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface ReviewItemJpaRepository extends JpaRepository<ReviewItemRow, String> {

    List<ReviewItemRow> findByStateOrderByRaisedAtAsc(String state, Limit limit);

    long countBySourceId(String sourceId);

    long countBySourceIdAndOutcome(String sourceId, String outcome);
}

/** Driven adapter for the disagreement queue. */
@Repository
class JpaReviewQueue implements ReviewQueue {

    private final ReviewItemJpaRepository rows;

    JpaReviewQueue(ReviewItemJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<ReviewItem> findById(ReviewItemId id) {
        return rows.findById(id.value()).map(JpaReviewQueue::toDomain);
    }

    @Override
    public List<ReviewItem> pending(int limit) {
        return rows.findByStateOrderByRaisedAtAsc(ReviewState.PENDING.name(), Limit.of(limit))
                .stream()
                .map(JpaReviewQueue::toDomain)
                .toList();
    }

    @Override
    public long countRaisedBySource(String sourceId) {
        return rows.countBySourceId(sourceId);
    }

    @Override
    public long countResolvedBySource(String sourceId, ReviewOutcome outcome) {
        return rows.countBySourceIdAndOutcome(sourceId, outcome.name());
    }

    @Override
    public void save(ReviewItem item) {
        ReviewItemRow row = rows.findById(item.id().value()).orElseGet(ReviewItemRow::new);
        row.reviewItemId = item.id().value();
        row.relationshipId = item.edge().value();
        row.proposedModel = item.proposedBy().model();
        row.proposedFamily = item.proposedBy().family();
        row.dissentModel = item.dissentedBy().model();
        row.dissentFamily = item.dissentedBy().family();
        row.disagreement = item.disagreement();
        row.sourceId = item.sourceId();
        row.raisedAt = item.raisedAt();
        row.state = item.state().name();
        row.claimedBy = item.claimedBy();
        row.outcome = item.outcome() == null ? null : item.outcome().name();
        row.resolvedBy = item.resolvedBy();
        row.resolvedAt = item.resolvedAt();
        rows.save(row);
    }

    private static ReviewItem toDomain(ReviewItemRow row) {
        ReviewItem item = new ReviewItem(
                ReviewItemId.of(row.reviewItemId),
                RelationshipId.of(row.relationshipId),
                ExtractorId.model(row.proposedModel, row.proposedFamily),
                ExtractorId.model(row.dissentModel, row.dissentFamily),
                row.disagreement,
                row.sourceId,
                row.raisedAt);

        item.rehydrate(
                ReviewState.valueOf(row.state),
                row.claimedBy,
                row.outcome == null ? null : ReviewOutcome.valueOf(row.outcome),
                row.resolvedBy,
                row.resolvedAt);
        return item;
    }
}
