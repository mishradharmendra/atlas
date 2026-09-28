package com.atlas.metering.domain.port;

import com.atlas.metering.domain.model.TenantUsage;
import com.atlas.metering.domain.model.UsageRecord;
import com.atlas.shared.identity.TenantId;

/** Append-only ledger of what was consumed. */
public interface UsageLedger {

    /**
     * Appends a record, ignoring a repeat of one already present.
     *
     * <p>Idempotent rather than "the caller should not do that". Events are redelivered as a
     * matter of course, and a double-counted step is invisible — it looks exactly like a step
     * that cost twice as much, and nobody notices until the provider invoice disagrees.
     */
    void record(UsageRecord usage);

    /**
     * Raw consumption for a period. Priced by the caller, which holds the subscription.
     *
     * <p>The ledger deliberately does not know what a seat costs: that is a commercial fact with
     * its own history, and a ledger that cached it would answer September's margin with today's
     * price.
     */
    TenantUsage usageFor(TenantId tenant, String period);

    /** Total spend in a period. What a budget policy is evaluated against. */
    long spentMicros(TenantId tenant, String period);
}
