/**
 * Lifecycle events published by the catalog.
 *
 * <p>Exposed separately from {@code api} because the relationship is different: {@code api} is
 * called, {@code events} is subscribed to. A module that reacts to a retraction has no business
 * reaching into the catalog's internals to do it.
 */
@org.springframework.modulith.NamedInterface("events")
package com.atlas.catalog.events;
