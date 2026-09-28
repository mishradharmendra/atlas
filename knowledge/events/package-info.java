/**
 * Lifecycle events published by the knowledge graph.
 *
 * <p>Exposed separately from {@code api} because the relationship is different: {@code api} is
 * called, {@code events} is subscribed to. Identifiers are strings rather than domain types for
 * the same reason the catalog's are — a message whose payload is one module's internal type makes
 * every subscriber depend on those internals.
 */
@org.springframework.modulith.NamedInterface("events")
package com.atlas.knowledge.events;
