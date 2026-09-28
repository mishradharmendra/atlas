package com.atlas.knowledge.application;

import com.atlas.knowledge.api.HopView;
import com.atlas.knowledge.api.PathView;
import com.atlas.knowledge.domain.model.CanonicalEntity;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.port.EntityRepository;
import com.atlas.knowledge.domain.port.RelationshipRepository;
import com.atlas.shared.temporal.BitemporalAsOf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Breadth-first walk over edges visible at a pair of instants.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>This is the capability retrieval cannot provide. "Which suppliers do our portfolio companies
 * share?" is not a passage in any document; no amount of reranking finds it, because the answer is
 * a join and the documents each hold one row of it.
 *
 * <p>Depth is bounded and cycles are cut. Company graphs are dense — supplier, customer and
 * competitor edges make most of a sector reachable within four hops — so an unbounded walk returns
 * the corpus and calls it an answer.
 */
@Service
class GraphTraversal {

    /**
     * Beyond this, a path is not evidence.
     *
     * <p>Each hop carries its own extraction error. Even at a generous 95% per verified edge,
     * a five-hop path is right about three quarters of the time, and nothing in the output
     * distinguishes those from the rest.
     */
    static final int MAX_SUPPORTED_HOPS = 4;

    private final RelationshipRepository relationships;
    private final EntityRepository entities;

    GraphTraversal(RelationshipRepository relationships, EntityRepository entities) {
        this.relationships = relationships;
        this.entities = entities;
    }

    List<PathView> pathsFrom(EntityId start, int maxHops, BitemporalAsOf at) {
        if (maxHops < 1) {
            throw new IllegalArgumentException("maxHops must be at least 1, was " + maxHops);
        }
        if (maxHops > MAX_SUPPORTED_HOPS) {
            throw new IllegalArgumentException(
                    "maxHops of %d exceeds the supported depth of %d; beyond that the compounded "
                                    + "per-edge error makes a path indistinguishable from a guess"
                            .formatted(maxHops, MAX_SUPPORTED_HOPS));
        }

        List<PathView> paths = new ArrayList<>();
        Deque<List<Relationship>> frontier = new ArrayDeque<>();
        frontier.add(List.of());

        while (!frontier.isEmpty()) {
            List<Relationship> prefix = frontier.poll();
            if (prefix.size() >= maxHops) {
                continue;
            }
            EntityId tip = currentTip(prefix, start);

            for (Relationship edge : relationships.neighbours(tip, at)) {
                EntityId next = otherEnd(edge, tip);
                if (visits(prefix, start).contains(next)) {
                    // A cycle. Returning it would present "A supplies B supplies A" as a finding.
                    continue;
                }
                List<Relationship> extended = new ArrayList<>(prefix);
                extended.add(edge);
                paths.add(toPath(start, extended));
                frontier.add(List.copyOf(extended));
            }
        }
        return List.copyOf(paths);
    }

    private static EntityId currentTip(List<Relationship> prefix, EntityId start) {
        EntityId tip = start;
        for (Relationship edge : prefix) {
            tip = otherEnd(edge, tip);
        }
        return tip;
    }

    private static Set<EntityId> visits(List<Relationship> prefix, EntityId start) {
        Set<EntityId> seen = new HashSet<>();
        seen.add(start);
        EntityId tip = start;
        for (Relationship edge : prefix) {
            tip = otherEnd(edge, tip);
            seen.add(tip);
        }
        return seen;
    }

    /** Edges are stored once; a symmetric predicate is walked from whichever end you arrived at. */
    private static EntityId otherEnd(Relationship edge, EntityId from) {
        return edge.subject().equals(from) ? edge.object() : edge.subject();
    }

    private PathView toPath(EntityId start, List<Relationship> edges) {
        List<HopView> hops = new ArrayList<>();
        EntityId tip = start;
        for (Relationship edge : edges) {
            EntityId next = otherEnd(edge, tip);
            hops.add(new HopView(
                    tip.value(),
                    displayName(tip),
                    edge.type().name(),
                    next.value(),
                    displayName(next),
                    edge.provenance()));
            tip = next;
        }
        return new PathView(start.value(), tip.value(), hops);
    }

    private String displayName(EntityId id) {
        return entities.findById(id).map(CanonicalEntity::canonicalName).orElse(id.value());
    }
}
