package com.atlas.metering.domain.model;

import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * One immutable line in the ledger: what a single step attempt consumed.
 *
 * <h2>Why the key is (run, step, attempt)</h2>
 *
 * <p>The same natural key the research trace uses, and for the same reason: once a step can be
 * redelivered, the ledger must be able to recognise a duplicate. A double-counted step is
 * invisible — it looks exactly like a step that genuinely cost twice as much, and the first place
 * anyone notices is a reconciliation against the provider invoice weeks later.
 *
 * <h2>Why cost is attributed per step</h2>
 *
 * <p>"This tenant spent four thousand dollars" cannot be acted on. "Retrieval was three thousand
 * of it, because the reranker is being called on every candidate" can. Attribution is the
 * difference between a number and a decision, and the step is the finest grain the trace
 * actually knows — the task type lives on the skill, not on what was executed.
 */
public record UsageRecord(
        TenantId tenant,
        String runId,
        String stepId,
        int attempt,
        long tokens,
        long costMicros,
        Instant incurredAt) {

    public UsageRecord {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(incurredAt, "incurred-at");
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("a usage record must name its run");
        }
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException(
                    "a usage record must name the step it paid for; an unattributed total is a "
                            + "number nobody can act on");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempts are counted from 1, was " + attempt);
        }
        if (tokens < 0 || costMicros < 0) {
            throw new IllegalArgumentException(
                    "usage must not be negative; a correction is a compensating record, not an "
                            + "edit, or the ledger stops matching the invoice");
        }
    }

    /** The idempotency key. Two records with this key are the same spend seen twice. */
    public String naturalKey() {
        return "%s|%s|%d".formatted(runId, stepId, attempt);
    }
}
