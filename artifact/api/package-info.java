/**
 * How other modules create and read deliverables.
 *
 * <p>Research reaches this way rather than constructing an {@code Artifact} itself. A run that
 * built the aggregate directly would own its invariants — the citation rule, the override rule,
 * the append-only history — and those would then have to hold in every module that ever emits a
 * deliverable rather than in one.
 *
 * <p><b>The identifier is minted here, not accepted from the caller.</b> The previous shape had
 * the run store an artefact id supplied over HTTP, which meant a completed run could reference a
 * deliverable that was never created; the field was populated, every status read healthy, and
 * there was nothing on the other end of the pointer. An id returned by the thing that persisted
 * the row cannot dangle that way.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.artifact.api;
