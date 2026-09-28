/**
 * Tenancy: who the customer is, what they bought, and who holds a seat.
 *
 * <h2>Why subscriptions are temporal rather than current</h2>
 *
 * <p>The naive model stores {@code seats} on the tenant and updates it when the contract changes.
 * It works until the first billing dispute, which is always about a past period: a customer went
 * from ten seats to twenty in October, and the September invoice now recalculates against twenty.
 * The number was overwritten, so the platform cannot reconstruct what was true when the invoice
 * was raised, and the argument is settled by whoever has better records — which will not be the
 * vendor whose system overwrites.
 *
 * <p>So a subscription has a validity interval, changes append a new one, and "how many seats did
 * they have in September?" is a query rather than an archaeology exercise. This is the same
 * reasoning as the knowledge graph's world time, applied to a commercial fact.
 *
 * <h2>Why seat assignment is temporal too</h2>
 *
 * <p>Per-seat pricing means "who held a seat in September" is a billable fact, and staff move.
 * An assignment table that only records the present cannot answer it, and cannot show that a
 * departed employee's access ended when their seat was reassigned.
 */
package com.atlas.tenancy;
