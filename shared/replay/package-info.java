/**
 * Replay primitives: what must be pinned for an answer to be reproducible.
 *
 * <p>In the shared kernel rather than in the agent context because the same pin is needed by
 * evaluation (a benchmark result is meaningless without it), by retrieval (an index version is
 * part of it) and by the agent (a cache key is built from it). Three copies would drift, and the
 * drift would show up as an experiment that cannot be reproduced.
 */
@org.springframework.modulith.NamedInterface("replay")
package com.atlas.shared.replay;
