/**
 * Indexing: keeping the serving indexes consistent with the catalog.
 *
 * <h2>Why this is a module and not a method on the catalog</h2>
 *
 * <p>The catalog knows a document was retracted. It has no business knowing which indexes exist,
 * how many there are, or that one of them lives in another process written in another language.
 * Putting the purge inline would couple the corpus's system of record to every store in the
 * platform, and every new store would mean a change to the catalog.
 *
 * <h2>Why the listener is transactional</h2>
 *
 * <p>The cascade is asynchronous and the index is remote, so the two cannot share a transaction.
 * What they can share is a durable record of intent: Spring Modulith's event publication registry
 * writes the publication in the same transaction as the retraction, and marks it complete only
 * once the subscriber returns.
 *
 * <p>Without that, a crash between committing the retraction and reaching the index leaves a
 * document marked withdrawn in the catalog and still live in search — the platform reporting a
 * takedown it did not perform, which is the one outcome worse than not having performed it.
 */
package com.atlas.indexing;
