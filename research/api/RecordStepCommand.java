package com.atlas.research.api;

/**
 * What a step executor reports back after running one step.
 *
 * <p>Costs are mandatory and unsigned. A step that reported no cost would let a run exceed its
 * ceiling without the ceiling ever noticing, which is the failure the budget exists to prevent —
 * and the one that shows up as an invoice rather than an alert.
 */
public record RecordStepCommand(
        String runId,
        String executorId,
        String stepId,
        int attempt,
        String action,
        String observation,
        String outcome,
        String abstentionReason,
        String abstentionDetail,
        float abstentionCoverage,
        long tookMillis,
        long tokens,
        long costMicros) {

    public RecordStepCommand {
        if (tokens < 0 || costMicros < 0 || tookMillis < 0) {
            throw new IllegalArgumentException("a step cannot report negative consumption");
        }
    }
}
