/**
 * Metering: what each tenant actually costs to serve.
 *
 * <h2>Why this is a bounded context and not a dashboard query</h2>
 *
 * <p>Seat-based pricing combined with always-on agents is structurally dangerous. Revenue per
 * seat is flat; cost scales with autonomy and with how hard the questions are. A single team that
 * discovers the product and uses it properly can consume more than its subscription, and nothing
 * about that shows up as an error — it shows up as a margin that quietly inverts.
 *
 * <p>{@code RunBudget} already stops one run running away. It cannot answer the commercial
 * question, because that question is about the aggregate: this tenant, this month, across every
 * run and every seat.
 *
 * <h2>Why usage is recorded per step rather than per finished run</h2>
 *
 * <p>A run that never finishes still spent money. Accounting at terminal state would make every
 * orphaned, abandoned or crashed run free — and those are exactly the runs that went worst and
 * most deserve counting. Per-step accounting also means the ledger reconciles against a
 * provider's invoice, which is itemised per call.
 *
 * <h2>Why records are immutable</h2>
 *
 * <p>An editable ledger is not a ledger. A correction is a compensating record, so the history of
 * what was believed remains intact — the same reason the research trace is append-only.
 */
package com.atlas.metering;
