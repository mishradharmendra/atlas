/**
 * Agent memory: what a run is allowed to remember, and for how long.
 *
 * <h2>Why this is a bounded context and not a cache</h2>
 *
 * <p>A cache may be dropped at any time and nothing is lost. Memory cannot: an agent that
 * "remembers" Amazon's Q1 other assets is asserting a figure on a later run, without retrieving
 * it again and without the reader seeing where it came from. That makes a memory entry a claim,
 * and a claim needs the same three things every other claim in this platform needs — a source,
 * an entitlement, and a moment at which it stops being true.
 *
 * <h2>The invariant that matters most</h2>
 *
 * <p><b>A memory whose source is withdrawn must be forgettable, and must actually be forgotten.</b>
 * The takedown cascade already purges the index and flags deliverables. Memory is the third
 * place a withdrawn document survives, and the quietest: nothing in the artifact shows it, the
 * index no longer holds it, and the agent keeps asserting it from recall. G2.11 was this failure
 * one layer down — a retraction that looked applied and was not.
 *
 * <h2>Why recall fails closed</h2>
 *
 * <p>Recall takes the caller's permitted tags and returns nothing when they are empty. The
 * alternative — treating "no tags" as "no restriction" — makes a misconfigured caller read every
 * tenant's memory, and the symptom is more results rather than an error.
 */
package com.atlas.memory;
