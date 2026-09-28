package com.atlas.knowledge.domain.port;

import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.model.RelationshipId;
import com.atlas.shared.temporal.BitemporalAsOf;
import java.util.List;
import java.util.Optional;

/**
 * Durable store for extracted edges.
 *
 * <p>Note what is absent: there is no {@code neighbours(EntityId)}, no {@code findAll()}, and no
 * overload of {@link #neighbours} that omits the instant. That absence is the design, and it is
 * enforced by {@code TraversalRequiresAsOfTest} rather than left to reviewers.
 *
 * <p>A convenience overload defaulting to "now" would be added within a week of the first
 * deadline, and every caller that took it would produce answers that cannot be reproduced
 * tomorrow. Because the parameter is unavoidable, the instant a query was asked about is always
 * recoverable from the code that asked it.
 */
public interface RelationshipRepository {

    Optional<Relationship> findById(RelationshipId id);

    /**
     * Edges out of an entity that are visible at both instants.
     *
     * <p>Symmetric predicates are matched in either direction — an edge stored as B competes-with A
     * is returned when traversing from A — because storing both directions would double every
     * retraction and let the two halves drift apart.
     */
    List<Relationship> neighbours(EntityId from, BitemporalAsOf at);

    /**
     * Every edge whose provenance touches a document, regardless of status.
     *
     * <p>Status-blind on purpose: the cascade must reach withheld edges too. A withheld edge
     * derived from a withdrawn document is still stored, still visible in the review queue, and
     * still capable of being verified into visibility by a reviewer who has no idea the source is
     * gone.
     */
    List<Relationship> findDerivedFrom(String documentId);

    void save(Relationship relationship);
}
