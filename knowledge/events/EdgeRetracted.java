package com.atlas.knowledge.events;

import java.time.Instant;

/**
 * An edge has been withdrawn, most often because the document it came from was.
 *
 * <p>The second hop of the takedown cascade. Subscribers that cached a traversal result, or that
 * built a deliverable containing this path, need to know — the graph is one of the derived
 * artefacts a retraction has to reach, and it is the one most easily forgotten because nothing
 * about a withdrawn PDF looks like an edge between two companies.
 */
public record EdgeRetracted(
        String relationshipId, String subjectId, String objectId, String reason, Instant at) {}
