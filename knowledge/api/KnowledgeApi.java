package com.atlas.knowledge.api;

import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.BitemporalAsOf;
import java.util.List;
/**
 * The knowledge graph as the rest of the platform sees it.
 *
 * <p>Every read takes its instants explicitly. There is no overload that omits them, here or on
 * the ports beneath.
 */
public interface KnowledgeApi {

    /** Turns a name into an entity, or refuses and says why. */
    Resolution resolve(String mention, AsOf at);

    /**
     * Paths from an entity, up to {@code maxHops}.
     *
     * <p>A depth limit rather than an unbounded walk: in a graph of companies and suppliers,
     * unbounded traversal reaches most of the corpus within four hops and the result stops being
     * an answer.
     */
    List<PathView> pathsFrom(String entityId, int maxHops, BitemporalAsOf at);

    /** Records a proposed edge. Returns the id of the withheld row. */
    String propose(ProposeEdgeCommand command);

    /**
     * Records a second extractor's independent reading of the same span.
     *
     * <p>One method for both agreement and disagreement, because they are one decision made by one
     * comparison, and splitting them invites a caller to do the comparison itself and call only
     * the happy path — which is how a disagreement stops reaching the queue.
     */
    VerificationOutcome corroborate(CorroborationCommand command);

    /**
     * What a source's extractions have been worth, counted from the review queue.
     *
     * <p>On the knowledge API rather than in a separate reporting service because the numbers are
     * a by-product of the verification the graph already does. Put anywhere else, it becomes a
     * job that has to be scheduled, and a job that has to be scheduled is a job that silently
     * stops.
     */
    SourceQuality sourceQuality(String sourceId);

    /** Every source that has produced an edge. The vendor-comparison view. */
    List<SourceQuality> allSourceQuality();
}
