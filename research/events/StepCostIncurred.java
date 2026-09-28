package com.atlas.research.events;

import java.time.Instant;

/**
 * A step attempt consumed tokens and money.
 *
 * <p>Published on every recorded step, including failures and including steps of runs that later
 * suspend or are abandoned. Accounting only at terminal state would make every orphaned run free,
 * and those are the runs that went worst.
 *
 * <p>Carries {@code stepId} and {@code attempt} so a subscriber can recognise a redelivery. A
 * double-counted step is invisible: it looks exactly like a step that genuinely cost twice as
 * much, and nobody notices until the provider invoice disagrees.
 */
public record StepCostIncurred(
        String runId,
        String tenantId,
        String stepId,
        int attempt,
        long tokens,
        long costMicros,
        Instant at) {}
