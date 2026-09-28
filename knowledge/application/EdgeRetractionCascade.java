package com.atlas.knowledge.application;

import com.atlas.catalog.events.DocumentRetracted;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.port.RelationshipRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The hop of the takedown cascade that reaches the graph.
 *
 * <p>The one teams forget. Purging a withdrawn document from the search indexes is obvious;
 * remembering that a sentence in it became an edge between two companies, which is now part of a
 * three-hop answer in a client deck, is not. Nothing about the edge looks like the PDF it came
 * from, which is exactly why the citation on every edge is mandatory — it is the only thread back.
 *
 * <p>Withheld edges are retracted too. A withheld edge derived from a withdrawn document is still
 * sitting in the review queue, and a reviewer with no knowledge of the takedown would happily
 * verify it into visibility.
 */
@Component
class EdgeRetractionCascade {

    private static final Logger log = LoggerFactory.getLogger(EdgeRetractionCascade.class);

    private final RelationshipRepository relationships;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EdgeRetractionCascade(
            RelationshipRepository relationships, ApplicationEventPublisher events, Clock clock) {
        this.relationships = relationships;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Carries no {@code @Transactional}: {@code @ApplicationModuleListener} already means
     * {@code REQUIRES_NEW}, and adding a second one fails the context at startup.
     */
    @ApplicationModuleListener
    void on(DocumentRetracted event) {
        List<Relationship> derived = relationships.findDerivedFrom(event.documentId());
        String reason = "source document %s retracted: %s".formatted(event.documentId(), event.reason());

        for (Relationship edge : derived) {
            edge.retract(reason, clock.instant());
            relationships.save(edge);
            edge.drainEvents().forEach(events::publishEvent);
        }

        // Info because this is an auditable compliance action. The question asked months later is
        // "did any of our published conclusions rest on this document?", and the edge count is the
        // first half of that answer.
        log.info(
                "retraction cascade: retracted {} graph edge(s) derived from document {} ({})",
                derived.size(),
                event.documentId(),
                event.reason());
    }
}
