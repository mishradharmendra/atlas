package com.atlas.knowledge.application;

import com.atlas.knowledge.domain.model.ExtractorId;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.model.ReviewItem;
import com.atlas.knowledge.domain.model.ReviewItemId;
import com.atlas.knowledge.domain.model.ReviewOutcome;
import com.atlas.knowledge.domain.model.Verification;
import com.atlas.knowledge.domain.port.RelationshipRepository;
import com.atlas.knowledge.domain.port.ReviewQueue;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a reviewer's decision to the edge it concerns.
 *
 * <p>The reviewer becomes the second extractor, in the {@code human} family. That is not a
 * formality: it means a human confirmation travels the same {@link Verification} path as a model
 * one and is subject to the same different-family rule, so there is no second way to make an edge
 * visible and no back door to audit separately.
 */
@Service
class ReviewResolution {

    private final ReviewQueue queue;
    private final RelationshipRepository relationships;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReviewResolution(
            ReviewQueue queue,
            RelationshipRepository relationships,
            ApplicationEventPublisher events,
            Clock clock) {
        this.queue = queue;
        this.relationships = relationships;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void claim(String reviewItemId, String reviewer) {
        ReviewItem item = load(reviewItemId);
        item.claim(reviewer);
        queue.save(item);
    }

    /**
     * Settles a disputed edge.
     *
     * <p>Both aggregates are written in one transaction. Split across two, a crash between them
     * leaves either an item marked resolved whose edge is still withheld — invisible work, done
     * twice — or a verified edge with no record of who decided it.
     */
    @Transactional
    public void resolve(String reviewItemId, ReviewOutcome decision, String reviewer) {
        ReviewItem item = load(reviewItemId);
        Instant now = clock.instant();
        item.resolve(decision, reviewer, now);
        queue.save(item);

        Relationship edge = relationships
                .findById(item.edge())
                .orElseThrow(() -> new NoSuchElementException("no such relationship: " + item.edge()));

        switch (decision) {
            case CONFIRMED -> {
                edge.verify(new Verification(
                        item.proposedBy(),
                        ExtractorId.human(reviewer),
                        now,
                        "review %s confirmed the proposal over: %s"
                                .formatted(item.id(), item.disagreement())));
                relationships.save(edge);
                edge.drainEvents().forEach(events::publishEvent);
            }
            case REFUTED -> {
                edge.reject("review %s: %s".formatted(item.id(), item.disagreement()), now);
                relationships.save(edge);
            }
            case UNDECIDABLE -> {
                // The edge stays withheld and the item stays resolved. Nothing to write on the
                // edge: "the source does not settle this" is a fact about the document, and
                // recording it as a rejection would charge it against the extractor's accuracy.
            }
        }
    }

    private ReviewItem load(String reviewItemId) {
        return queue.findById(ReviewItemId.of(reviewItemId))
                .orElseThrow(() -> new NoSuchElementException("no such review item: " + reviewItemId));
    }
}
