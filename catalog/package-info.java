/**
 * Catalog: what exists in the corpus, and what state it is in.
 *
 * <h2>Why lifecycle is an aggregate and not a status column</h2>
 *
 * <p>Documents do not simply exist. They get restated, superseded by a later filing, and withdrawn
 * by publishers — and each of those has consequences that reach every derived artefact.
 *
 * <p>A retraction in particular has to travel backwards along the provenance chain: out of the
 * lexical and dense indexes, out of the graph edges extracted from it, out of any agent memory that
 * cached it, and onto a flag against the deliverables that cited it. That cascade is only possible
 * because every derived artefact records the span it came from and every span records its document.
 *
 * <p>Modelling this as a mutable status field would make the transitions unguarded. As an
 * aggregate, "retracted is terminal" and "superseded must name its successor" are enforced rather
 * than remembered.
 *
 * <h2>Relationship to the rest</h2>
 *
 * <p>Catalog is downstream of {@code ingestion}: it consumes acquisition events and never reaches
 * back. It holds no content — only identity, classification, lifecycle and the pointer into the
 * landing zone — because content lives in the object store and a catalog row that duplicated it
 * would immediately begin to drift.
 */
package com.atlas.catalog;
