package com.atlas.research.api;

import java.util.List;
import java.util.UUID;

/**
 * A request to start a run.
 *
 * <p>The skill version is explicit, not resolved to "latest" here. Resolving it at start time
 * would mean two runs a week apart could not be compared — the procedure would silently differ
 * while the comparison looked valid.
 */
public record StartRunCommand(
        UUID tenantId,
        UUID principalId,
        String question,
        String skillId,
        int skillVersion,
        String model,
        String promptVersion,
        String indexVersion,
        String retrievalParams,
        long maxTokens,
        long maxWallMillis,
        long maxSpendMicros,
        /**
         * Classifications the caller's credential is barred from, taken from the verified
         * snapshot rather than from the body. Empty when nothing is denied.
         */
        java.util.Set<String> deniedLabels) {

    public StartRunCommand {
        if (skillVersion < 1) {
            throw new IllegalArgumentException(
                    "a run must name the skill version it executes; 'latest' makes two runs "
                            + "incomparable while looking comparable");
        }
        // Absent means "nothing denied", which is the safe reading: a skill with an MNPI
        // guardrail then refuses to start rather than assuming a restriction nobody proved.
        deniedLabels = java.util.Set.copyOf(
                java.util.Objects.requireNonNullElse(deniedLabels, java.util.Set.<String>of()));
    }

    public List<String> requiredFingerprintComponents() {
        return List.of(model, promptVersion, indexVersion, retrievalParams);
    }
}
