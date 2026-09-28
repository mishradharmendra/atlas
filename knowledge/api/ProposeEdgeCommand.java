package com.atlas.knowledge.api;

import com.atlas.shared.provenance.Citation;
import com.atlas.shared.temporal.Validity;
import java.util.List;

/**
 * An edge an extractor proposes. Not an edge the graph will return.
 *
 * <p>Named for what it is. {@code CreateEdgeCommand} would suggest the caller gets to decide
 * whether the edge exists; what a caller actually gets is a withheld row and a place in the
 * verification queue.
 *
 * <p>There is deliberately no status field. Letting the caller supply one would make the whole
 * withheld-by-default design a convention that the first pipeline under deadline pressure would
 * opt out of.
 */
public record ProposeEdgeCommand(
        String subjectEntityId,
        String relationshipType,
        String objectEntityId,
        Validity validity,
        String extractorModel,
        String extractorFamily,
        String sourceId,
        List<Citation> evidence) {

    public ProposeEdgeCommand {
        evidence = List.copyOf(evidence);
    }
}
