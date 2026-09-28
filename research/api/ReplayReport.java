package com.atlas.research.api;

import java.util.List;
import java.util.SortedMap;

/**
 * The result of replaying a run: whether it reproduced, and if not, exactly where it diverged.
 *
 * <p>A boolean would be useless. "The replay did not match" sends someone reading two traces side
 * by side; "step 3 abstained where it previously concluded, under a changed index version" is the
 * answer. Divergence is normal and expected — models are stochastic and indexes are rebuilt — so
 * the useful output is an explanation, not a verdict.
 */
public record ReplayReport(
        String runId,
        boolean reproduced,
        int stepsCompared,
        List<Divergence> divergences,
        SortedMap<String, String> configurationChanged) {

    public ReplayReport {
        divergences = List.copyOf(divergences);
    }

    /** One step that behaved differently on replay. */
    public record Divergence(int sequence, String stepId, String was, String now, String note) {}

    /**
     * Whether the difference is explained by configuration rather than by the system regressing.
     *
     * <p>The distinction people skip. A replay under a new model that produces a different answer
     * is not a bug; a replay under an identical fingerprint that produces a different answer is
     * either non-determinism nobody accounted for or a genuine defect, and it is worth an alert.
     */
    public boolean divergedUnderIdenticalConfiguration() {
        return !reproduced && configurationChanged.isEmpty();
    }
}
