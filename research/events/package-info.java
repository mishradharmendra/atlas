/**
 * Events published by the research context.
 *
 * <p>Exposed separately from {@code api} because the relationship is different: {@code api} is
 * called, {@code events} is subscribed to.
 */
@org.springframework.modulith.NamedInterface("events")
package com.atlas.research.events;
