package com.atlas.catalog.events;

import java.time.Instant;

/**
 * A document has been withdrawn. Drives the retraction cascade.
 *
 * <p>Subscribers must remove it from the lexical and dense indexes, mark graph edges derived from
 * it as retracted, evict it from agent memory, and flag deliverables that cited it.
 *
 * <p>The last of those is the one teams forget. A broker withdrawing a note does not un-send the
 * client deck that quoted it, so the deliverable has to carry a warning — which is only possible
 * because every cell and sentence records the span it came from.
 *
 * <p>Identifiers are strings, not {@code DocumentId}. An event is a message to other modules, and a
 * message whose payload is one module's internal type makes every subscriber depend on those
 * internals — which is the coupling the event exists to avoid.
 */
public record DocumentRetracted(String documentId, String sourceId, String reason, Instant at) {}
