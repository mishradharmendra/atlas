package com.atlas.knowledge.api;

import com.atlas.shared.provenance.Citation;
import java.util.List;

/**
 * One step of a traversal, carrying the evidence that licenses it.
 *
 * <p>Provenance travels with every hop rather than being attached to the path as a whole. A
 * three-hop answer supported by three documents is only checkable if you can tell which document
 * supports which hop — and when one of them is retracted, only per-hop provenance identifies which
 * part of the conclusion died.
 */
public record HopView(
        String subjectId,
        String subjectName,
        String relationshipType,
        String objectId,
        String objectName,
        List<Citation> evidence) {

    public HopView {
        evidence = List.copyOf(evidence);
    }
}
