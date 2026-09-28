package com.atlas.knowledge.api;

import java.util.List;

/**
 * A path through the graph: the thing a document search cannot produce.
 *
 * <p>"Which of our portfolio companies are exposed to a supplier in Taiwan?" is not in any single
 * document. It is a join across many, and this is its answer type.
 */
public record PathView(String fromEntityId, String toEntityId, List<HopView> hops) {

    public PathView {
        hops = List.copyOf(hops);
        if (hops.isEmpty()) {
            throw new IllegalArgumentException("a path must have at least one hop");
        }
    }

    public int length() {
        return hops.size();
    }

    /**
     * Every document this path depends on.
     *
     * <p>The set a retraction is checked against, and the list an analyst is shown when they ask
     * what a conclusion rests on.
     */
    public List<String> supportingDocuments() {
        return hops.stream()
                .flatMap(hop -> hop.evidence().stream())
                .map(citation -> citation.span().docId())
                .distinct()
                .toList();
    }
}
