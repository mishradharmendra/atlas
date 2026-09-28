package com.atlas.knowledge.domain.port;

import com.atlas.knowledge.domain.model.ReviewItem;
import com.atlas.knowledge.domain.model.ReviewItemId;
import com.atlas.knowledge.domain.model.ReviewOutcome;
import java.util.List;
import java.util.Optional;

/** Durable store for the disagreement queue. */
public interface ReviewQueue {

    Optional<ReviewItem> findById(ReviewItemId id);

    /** Oldest first: a queue worked newest-first starves exactly the hard cases. */
    List<ReviewItem> pending(int limit);

    /** The denominator for per-source disagreement rate. */
    long countRaisedBySource(String sourceId);

    long countResolvedBySource(String sourceId, ReviewOutcome outcome);

    void save(ReviewItem item);
}
