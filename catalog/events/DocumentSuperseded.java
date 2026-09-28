package com.atlas.catalog.events;

import java.time.Instant;

/**
 * A later document replaces this one. The original stays citable.
 *
 * <p>Point-in-time questions legitimately need what was reported at the time, and the fact of a
 * restatement is itself a signal — surfacing it as a conflict is more useful than hiding the
 * original.
 */
public record DocumentSuperseded(String documentId, String successorId, Instant at) {}
