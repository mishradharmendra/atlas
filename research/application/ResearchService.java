package com.atlas.research.application;

import com.atlas.artifact.api.ArtifactApi;
import com.atlas.artifact.api.ArtifactView;
import com.atlas.artifact.api.NewArtifact;
import com.atlas.artifact.api.NewCell;
import com.atlas.research.api.RecordStepCommand;
import com.atlas.research.api.ReplayReport;
import com.atlas.research.api.ResearchApi;
import com.atlas.research.api.RunView;
import com.atlas.research.api.GuardrailNotSatisfiedException;
import com.atlas.research.api.StartRunCommand;
import com.atlas.research.api.TraceEntryView;
import com.atlas.research.domain.model.Run;
import com.atlas.research.domain.model.RunId;
import com.atlas.research.domain.model.RunLease;
import com.atlas.research.domain.model.StepOutcome;
import com.atlas.research.domain.model.TraceEntry;
import com.atlas.research.domain.port.RunRepository;
import com.atlas.shared.budget.RunBudget;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.outcome.Abstention;
import com.atlas.shared.outcome.AbstentionReason;
import com.atlas.shared.replay.ConfigFingerprint;
import com.atlas.skill.api.SkillApi;
import com.atlas.skill.api.SkillView;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for research runs.
 *
 * <p>Owns durable state and invariants; executes nothing. Steps run in the compute plane and
 * report back here, which is what keeps model routing out of the domain's release cadence.
 */
@Service
class ResearchService implements ResearchApi {

    private final RunRepository runs;
    private final SkillApi skills;
    private final ArtifactApi artifacts;
    private final ReplayComparator replayComparator;
    private final OrphanedRunReaper reaper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ResearchService(
            RunRepository runs,
            SkillApi skills,
            ArtifactApi artifacts,
            ReplayComparator replayComparator,
            OrphanedRunReaper reaper,
            ApplicationEventPublisher events,
            Clock clock) {
        this.runs = runs;
        this.skills = skills;
        this.artifacts = artifacts;
        this.replayComparator = replayComparator;
        this.reaper = reaper;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String start(StartRunCommand command) {
        // Through skill's published interface, not its aggregate. Modulith caught the first
        // version reaching for the repository: an aggregate handed across a module boundary is
        // one whose invariants become every caller's problem.
        SkillView skill = skills.find(command.skillId(), command.skillVersion());

        if (!skill.frozen()) {
            // A draft can still be edited, so any grade the run earns describes a procedure that
            // may no longer exist by the time anyone reads it.
            throw new IllegalStateException(
                    "skill %s is a draft; freeze it before running against it, or its results "
                            .formatted(skill.versionedId())
                            + "describe a procedure that can still change");
        }

        // A procedure that must not commingle MNPI may only run under a credential that cannot
        // reach MNPI. Checked here rather than at the artefact, because by then the material has
        // already been retrieved, held in memory and quoted into cells -- the commingling has
        // happened and all that is left is deciding whether to publish it. Refusing the run is
        // the only point where nothing has been mixed yet.
        if (skill.guardrails().contains("NO_MNPI_COMMINGLING")
                && !command.deniedLabels().contains("mnpi")) {
            throw new GuardrailNotSatisfiedException(
                    ("skill %s declares NO_MNPI_COMMINGLING and this credential is not denied "
                                    + "the 'mnpi' label. Run it under a snapshot that denies "
                                    + "mnpi: a guardrail the platform cannot enforce "
                                    + "structurally is a note in a document.")
                            .formatted(skill.versionedId()));
        }

        RunId id = RunId.of("run-" + UUID.randomUUID());
        Run run = new Run(
                id,
                new TenantId(command.tenantId()),
                new PrincipalId(command.principalId()),
                command.question(),
                skill.versionedId(),
                skill.mandatoryStepIds(),
                ConfigFingerprint.of(
                                command.model(),
                                command.promptVersion(),
                                command.indexVersion(),
                                command.retrievalParams())
                        .with("skill", skill.versionedId()),
                clock.instant(),
                new RunBudget(
                        command.maxTokens(),
                        Duration.ofMillis(command.maxWallMillis()),
                        command.maxSpendMicros()));

        run.begin();
        runs.save(run);
        return id.value();
    }

    @Override
    @Transactional
    public RunView claim(String runId, String executorId) {
        Run run = load(runId);
        run.claim(executorId, clock.instant(), RunLease.DEFAULT_DURATION);
        runs.save(run);
        return view(run);
    }

    @Override
    @Transactional
    public RunView heartbeat(String runId, String executorId) {
        Run run = load(runId);
        run.renewLease(executorId, clock.instant(), RunLease.DEFAULT_DURATION);
        runs.save(run);
        return view(run);
    }

    @Override
    @Transactional
    public String recordStep(RecordStepCommand command) {
        Run run = load(command.runId());
        StepOutcome outcome = StepOutcome.valueOf(command.outcome());

        run.record(
                new TraceEntry(
                        run.trace().size(),
                        command.stepId(),
                        command.attempt(),
                        clock.instant(),
                        Duration.ofMillis(command.tookMillis()),
                        command.action(),
                        command.observation(),
                        outcome,
                        outcome == StepOutcome.ABSTAINED ? abstentionOf(command) : null,
                        command.tokens(),
                        command.costMicros()),
                command.executorId());

        runs.save(run);
        run.drainEvents().forEach(events::publishEvent);
        return run.status().name();
    }

    private static Abstention abstentionOf(RecordStepCommand command) {
        if (command.abstentionReason() == null || command.abstentionReason().isBlank()) {
            throw new IllegalArgumentException(
                    ("step '%s' abstained without a reason. An unexplained abstention cannot be "
                                    + "told apart from a crash, and one is a correct answer while "
                                    + "the other is an outage")
                            .formatted(command.stepId()));
        }
        return new Abstention(
                command.stepId(),
                AbstentionReason.valueOf(command.abstentionReason()),
                command.abstentionDetail(),
                command.abstentionCoverage());
    }

    @Override
    @Transactional
    public RunView complete(String runId) {
        Run run = load(runId);
        run.complete();
        runs.save(run);
        return view(run);
    }

    @Override
    @Transactional
    public RunView emitArtefact(String runId, String title, List<NewCell> cells) {
        Run run = load(runId);

        // Checked before anything is built. Emitting spans two modules, and validating at the end
        // would leave a persisted deliverable behind for a run that was refused one.
        run.requireArtefactReady(); // throws MandatoryStepAbstainedException, deliberately

        ArtifactView artifact = artifacts.create(
                new NewArtifact(run.tenant().value().toString(), runId, title, cells));

        run.emitArtefact(artifact.artifactId());
        runs.save(run);
        return view(run);
    }

    @Override
    public RunView find(String runId) {
        return view(load(runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TraceEntryView> trace(String runId) {
        return load(runId).trace().stream()
                .map(entry -> new TraceEntryView(
                        entry.sequence(),
                        entry.stepId(),
                        entry.attempt(),
                        entry.startedAt(),
                        entry.took().toMillis(),
                        entry.action(),
                        entry.observation(),
                        entry.outcome().name(),
                        entry.abstention() == null ? null : entry.abstention().reason().name(),
                        entry.abstention() == null ? null : entry.abstention().detail(),
                        entry.abstention() != null && entry.abstention().isGroundTruth(),
                        entry.tokens(),
                        entry.costMicros()))
                .toList();
    }

    @Override
    public List<RunView> suspended(int limit) {
        return runs.suspended(limit).stream().map(ResearchService::view).toList();
    }

    @Override
    @Transactional
    public RunView resume(String runId, long maxTokens, long maxWallMillis, long maxSpendMicros) {
        Run run = load(runId);
        run.resume(new RunBudget(maxTokens, Duration.ofMillis(maxWallMillis), maxSpendMicros));
        runs.save(run);
        return view(run);
    }

    @Override
    public ReplayReport replay(
            String runId, String currentModel, String currentIndexVersion, List<String> observed) {
        return replayComparator.compare(load(runId), currentModel, currentIndexVersion, observed);
    }

    @Override
    public int reapOrphanedRuns() {
        return reaper.reap();
    }

    private Run load(String runId) {
        return runs.findById(RunId.of(runId))
                .orElseThrow(() -> new NoSuchElementException("no such run: " + runId));
    }

    static RunView view(Run run) {
        return new RunView(
                run.id().value(),
                run.tenant().value().toString(),
                run.question(),
                run.skillVersionedId(),
                run.fingerprint().value(),
                run.status().name(),
                run.statusReason(),
                run.artefactId(),
                run.totalTokens(),
                run.totalCostMicros(),
                run.isReplayable(),
                run.blockingSteps(),
                run.abstentions().stream().map(Abstention::detail).toList());
    }
}
