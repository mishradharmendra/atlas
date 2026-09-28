package com.atlas.evaluation.api;

import java.util.List;

/**
 * Open Host Service for evaluation.
 *
 * <p>The compute plane runs benchmarks and submits what it measured. Whether a submission is
 * admissible — frozen rubric, pinned baseline, comparable rubric version — is decided here, where
 * there is a transaction and an audit trail.
 */
public interface EvaluationApi {

    /**
     * Authors a rubric and freezes it.
     *
     * <p>Idempotent on (id, version): republishing an identical rubric succeeds, because a
     * benchmark's publish step runs on every CI build and failing the second one would make
     * the step something people comment out.
     *
     * @return the share of positive weight on temporal, authority and breadth
     * @throws IllegalStateException if the rubric cannot be frozen, or if a different rubric
     *     already exists under this id and version
     */
    int publishRubric(RubricDefinition definition);

    /**
     * Records a completed run and gates it against its pinned baseline.
     *
     * @return the slices that regressed. Empty means the gate passed.
     * @throws IllegalStateException if the rubric is not frozen, which would make the result
     *     unfalsifiable rather than merely provisional
     */
    List<String> recordRun(EvalRunSubmission submission);

    /** Pins a run's results as the new reference point. Refused if the id is already pinned. */
    void pinBaseline(String baselineId, String evalRunId);
}
