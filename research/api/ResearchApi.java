package com.atlas.research.api;

import com.atlas.artifact.api.NewCell;
import java.util.List;

/** Use cases for research runs. */
public interface ResearchApi {

    /** Starts a run against a named skill version. Returns the run id. */
    String start(StartRunCommand command);

    /**
     * Claims exclusive rights to drive a run, for the lease duration.
     *
     * <p>Refused while another executor holds an unexpired lease. Two executors on one run both
     * append steps and both charge the budget, which is worse than an orphan: it is an orphan
     * whose output looks complete.
     */
    RunView claim(String runId, String executorId);

    /** Extends the holder's lease. An executor that stops calling this is treated as dead. */
    RunView heartbeat(String runId, String executorId);

    /**
     * Records one completed step attempt.
     *
     * <p>Returns the run's status, because the answer the caller most needs is whether to keep
     * going: a step that breached the budget suspends the run, and an executor that carried on
     * would spend money on a run nobody is going to be shown.
     */
    String recordStep(RecordStepCommand command);

    /** Marks every mandatory step done. Refuses if any is still pending. */
    RunView complete(String runId);

    /**
     * Emits the artefact, or refuses with the steps that could not conclude.
     *
     * @throws com.atlas.research.domain.model.MandatoryStepAbstainedException when a mandatory
     *     step abstained, was skipped or degraded
     */
    RunView emitArtefact(String runId, String title, List<NewCell> cells);

    RunView find(String runId);

    /** The run's step-by-step trace. What a trace viewer renders. */
    List<TraceEntryView> trace(String runId);

    /** Runs stopped at a ceiling, waiting for someone to decide whether to raise it. */
    List<RunView> suspended(int limit);

    /** Resumes a suspended run under raised ceilings. */
    RunView resume(String runId, long maxTokens, long maxWallMillis, long maxSpendMicros);

    /**
     * Sweeps runs whose executor stopped renewing its lease. Returns how many were abandoned.
     *
     * <p>Runs on a schedule, and is exposed here as well because "we have just restarted the
     * workers, release everything they were holding" is an operator action, not a thing to wait
     * a sweep interval for.
     */
    int reapOrphanedRuns();

    /**
     * Replays a recorded run against the current configuration and reports where it diverged.
     *
     * <p>Takes the observed outcomes rather than executing steps itself: execution lives in the
     * compute plane, and a domain module that called models would collapse the plane split.
     */
    ReplayReport replay(String runId, String currentModel, String currentIndexVersion, List<String> observedOutcomes);
}
