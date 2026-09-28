package com.atlas.research.domain.model;

import com.atlas.shared.outcome.Abstention;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One entry in a run's trace: what the step saw, what it did, what came back, and what it cost.
 *
 * <p>State, action, observation, cost — the four fields a trajectory evaluation needs, recorded
 * because they were designed in rather than reconstructed from logs afterwards. Reconstruction is
 * always possible in principle and never works in practice: the one field you need is the one
 * nobody logged.
 *
 * <p>Costs are on the step rather than only on the run because "the run cost $4" cannot be acted
 * on. "Retrieval ran five times and cost $3 of it" can.
 */
public record TraceEntry(
        int sequence,
        String stepId,
        int attempt,
        Instant startedAt,
        Duration took,
        String action,
        String observation,
        StepOutcome outcome,
        Abstention abstention,
        long tokens,
        long costMicros) {

    public TraceEntry {
        if (sequence < 0) {
            throw new IllegalArgumentException("trace sequence must not be negative");
        }
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("a trace entry must name its step");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempts are counted from 1, was " + attempt);
        }
        Objects.requireNonNull(startedAt, "started-at");
        Objects.requireNonNull(took, "duration");
        Objects.requireNonNull(outcome, "outcome");

        if (outcome == StepOutcome.ABSTAINED && abstention == null) {
            throw new IllegalArgumentException(
                    ("step '%s' abstained without a reason. An unexplained abstention cannot be "
                                    + "told apart from a crash, and the two need different "
                                    + "responses: one is a correct answer, the other an outage")
                            .formatted(stepId));
        }
        if (outcome != StepOutcome.ABSTAINED && abstention != null) {
            throw new IllegalArgumentException(
                    "step '%s' carries an abstention but did not abstain".formatted(stepId));
        }
        if (tokens < 0 || costMicros < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
    }

    /** Whether this entry is the kind a replay must reproduce exactly. */
    public boolean isDeterministicallyReplayable() {
        return outcome != StepOutcome.FAILED;
    }

    public static List<TraceEntry> ordered(List<TraceEntry> entries) {
        return entries.stream().sorted((a, b) -> Integer.compare(a.sequence, b.sequence)).toList();
    }
}
