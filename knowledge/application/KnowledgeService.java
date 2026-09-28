package com.atlas.knowledge.application;

import com.atlas.knowledge.api.CorroborationCommand;
import com.atlas.knowledge.api.KnowledgeApi;
import com.atlas.knowledge.api.PathView;
import com.atlas.knowledge.api.ProposeEdgeCommand;
import com.atlas.knowledge.api.Resolution;
import com.atlas.knowledge.api.SourceQuality;
import com.atlas.knowledge.api.VerificationOutcome;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.ExtractorId;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.model.RelationshipId;
import com.atlas.knowledge.domain.model.RelationshipType;
import com.atlas.knowledge.domain.model.ReviewItem;
import com.atlas.knowledge.domain.model.ReviewItemId;
import com.atlas.knowledge.domain.model.Verification;
import com.atlas.knowledge.domain.port.RelationshipRepository;
import com.atlas.knowledge.domain.port.ReviewQueue;
import com.atlas.knowledge.domain.port.SourceQualityRepository;
import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.BitemporalAsOf;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the knowledge graph. */
@Service
class KnowledgeService implements KnowledgeApi {

    private final EntityResolver resolver;
    private final GraphTraversal traversal;
    private final RelationshipRepository relationships;
    private final ReviewQueue reviewQueue;
    private final SourceQualityRepository sourceQuality;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    KnowledgeService(
            EntityResolver resolver,
            GraphTraversal traversal,
            RelationshipRepository relationships,
            ReviewQueue reviewQueue,
            SourceQualityRepository sourceQuality,
            ApplicationEventPublisher events,
            Clock clock) {
        this.resolver = resolver;
        this.traversal = traversal;
        this.relationships = relationships;
        this.reviewQueue = reviewQueue;
        this.sourceQuality = sourceQuality;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public SourceQuality sourceQuality(String sourceId) {
        return sourceQuality.forSource(sourceId);
    }

    @Override
    public List<SourceQuality> allSourceQuality() {
        return sourceQuality.all();
    }

    @Override
    public Resolution resolve(String mention, AsOf at) {
        return resolver.resolve(mention, at);
    }

    @Override
    public List<PathView> pathsFrom(String entityId, int maxHops, BitemporalAsOf at) {
        return traversal.pathsFrom(EntityId.of(entityId), maxHops, at);
    }

    @Override
    @Transactional
    public String propose(ProposeEdgeCommand command) {
        RelationshipId id = RelationshipId.of("rel-" + UUID.randomUUID());
        Relationship edge = new Relationship(
                id,
                EntityId.of(command.subjectEntityId()),
                RelationshipType.valueOf(command.relationshipType()),
                EntityId.of(command.objectEntityId()),
                command.validity(),
                clock.instant(),
                ExtractorId.model(command.extractorModel(), command.extractorFamily()),
                command.evidence());
        relationships.save(edge);
        return id.value();
    }

    /**
     * Compares the second extractor's reading with the proposal and acts on the result.
     *
     * <p>The family check happens before the branch, not inside {@link Verification}. Doing it only
     * where agreement is constructed would leave the disagreement path unguarded: two same-family
     * extractors disagreeing would quietly raise a review item, and a human would spend time
     * adjudicating what is really one model's sampling noise.
     */
    @Override
    @Transactional
    public VerificationOutcome corroborate(CorroborationCommand command) {
        Relationship edge = relationships
                .findById(RelationshipId.of(command.relationshipId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "no such relationship: " + command.relationshipId()));

        ExtractorId second = ExtractorId.model(command.extractorModel(), command.extractorFamily());
        if (edge.extractedBy().sharesFamilyWith(second)) {
            throw new IllegalArgumentException(
                    ("%s cannot corroborate an edge extracted by %s: same model family, so their "
                                    + "errors are correlated and their agreement is not evidence")
                            .formatted(second, edge.extractedBy()));
        }

        Instant now = clock.instant();
        String disagreement = disagreementBetween(edge, command);

        if (disagreement == null) {
            edge.verify(new Verification(edge.extractedBy(), second, now, statementOf(edge)));
            relationships.save(edge);
            edge.drainEvents().forEach(events::publishEvent);
            return new VerificationOutcome.Verified(
                    edge.id().value(), edge.extractedBy().toString(), second.toString());
        }

        ReviewItem item = new ReviewItem(
                ReviewItemId.of("rev-" + UUID.randomUUID()),
                edge.id(),
                edge.extractedBy(),
                second,
                disagreement,
                sourceOf(edge),
                now);
        reviewQueue.save(item);
        return new VerificationOutcome.Disputed(edge.id().value(), item.id().value(), disagreement);
    }

    /** Null when the two readings match. */
    private static String disagreementBetween(Relationship edge, CorroborationCommand command) {
        if (command.assertsNothing()) {
            return "%s read no %s relationship in the cited span"
                    .formatted(command.extractorModel(), edge.type());
        }
        if (!edge.type().name().equals(command.assertedRelationshipType())) {
            return "predicate: proposed %s, second extractor read %s"
                    .formatted(edge.type(), command.assertedRelationshipType());
        }
        if (!edge.object().value().equals(command.assertedObjectEntityId())) {
            return "object: proposed %s, second extractor read %s"
                    .formatted(edge.object(), command.assertedObjectEntityId());
        }
        return null;
    }

    private static String statementOf(Relationship edge) {
        return "%s %s %s over %s".formatted(edge.subject(), edge.type(), edge.object(), edge.validity());
    }

    private static String sourceOf(Relationship edge) {
        return edge.provenance().getFirst().sourceName();
    }
}
