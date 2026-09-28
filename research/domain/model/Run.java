package com.atlas.research.domain.model;

import com.atlas.shared.budget.BudgetExceededException;
import com.atlas.shared.budget.RunBudget;
import com.atlas.research.events.RunSuspended;
import com.atlas.research.events.StepCostIncurred;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.outcome.Abstention;
import com.atlas.shared.replay.ConfigFingerprint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One execution of a skill: a forward-recovering saga with a trace and a budget.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>No artefact if a mandatory step abstained.</b> The rule that keeps the platform
 *       honest. A deliverable assembled from steps that could not establish their conclusions is
 *       the exact failure this domain punishes hardest: it looks complete, it is confidently
 *       worded, and the missing part is invisible at the point an analyst puts it in front of an
 *       investment committee.
 *   <li><b>A budget breach suspends; it never truncates.</b> See {@link RunStatus#SUSPENDED}.
 *   <li><b>The configuration is pinned at the start and cannot change mid-run.</b> Half a run on
 *       one model and half on another is not reproducible and not attributable.
 *   <li><b>The trace is append-only and densely sequenced.</b> A trace with a gap cannot be
 *       replayed, and a trace that can be rewritten is not evidence of anything.
 * </ol>
 */
public class Run {

    private final RunId id;
    private final TenantId tenant;
    private final PrincipalId principal;
    private final String question;
    private final String skillVersionedId;
    private final List<String> mandatoryStepIds;
    private final ConfigFingerprint fingerprint;
    private final Instant startedAt;

    // Not final: a resume replaces it. Leaving the breached ceiling in place would suspend the
    // run again on its next step, and the loop presents as a hang rather than as a refusal.
    private RunBudget budget;

    private final List<TraceEntry> trace = new ArrayList<>();
    private final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
    private final List<Object> domainEvents = new ArrayList<>();

    private RunStatus status = RunStatus.PLANNED;
    private String statusReason;
    private String artefactId;
    private RunLease lease;

    public Run(
            RunId id,
            TenantId tenant,
            PrincipalId principal,
            String question,
            String skillVersionedId,
            List<String> mandatoryStepIds,
            ConfigFingerprint fingerprint,
            Instant startedAt,
            RunBudget budget) {
        this.id = Objects.requireNonNull(id, "run id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.principal = Objects.requireNonNull(principal, "principal");
        this.fingerprint = Objects.requireNonNull(fingerprint, "config fingerprint");
        this.startedAt = Objects.requireNonNull(startedAt, "started-at");
        this.budget = Objects.requireNonNull(budget, "budget");

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("a run must record the question it answers");
        }
        if (skillVersionedId == null || !skillVersionedId.contains("@v")) {
            throw new IllegalArgumentException(
                    ("a run must pin the skill version it executed, as 'skill@vN', was '%s'. "
                                    + "Without the version, comparing two runs compares two "
                                    + "procedures that may differ")
                            .formatted(skillVersionedId));
        }
        if (mandatoryStepIds == null || mandatoryStepIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "a run must know which steps are mandatory, or the abstention rule cannot be "
                            + "applied and every run could emit an artefact having done nothing");
        }

        this.question = question;
        this.skillVersionedId = skillVersionedId;
        this.mandatoryStepIds = List.copyOf(mandatoryStepIds);
    }

    // -- lifecycle ---------------------------------------------------------

    public void begin() {
        if (status != RunStatus.PLANNED) {
            throw new IllegalStateException("run %s has already begun".formatted(id));
        }
        this.status = RunStatus.RUNNING;
    }

    /**
     * Grants an executor exclusive rights to drive this run.
     *
     * <p>Refuses while another executor holds an unexpired lease. That refusal is the whole
     * mechanism: without it a slow executor and its replacement both append steps, both charge
     * the budget, and the trace interleaves two attempts at the same work — an orphan whose
     * output looks complete.
     */
    public void claim(String executorId, Instant now, Duration duration) {
        requireRunning();
        if (lease != null && !lease.hasExpiredAt(now) && !lease.isHeldBy(executorId)) {
            throw new RunAlreadyClaimedException(id.value(), lease.heldBy(), lease.expiresAt());
        }
        this.lease = RunLease.granted(executorId, now, duration);
    }

    /** Extends the holder's claim. Refuses anyone else, including after expiry. */
    public void renewLease(String executorId, Instant now, Duration duration) {
        requireRunning();
        if (lease == null || !lease.isHeldBy(executorId)) {
            throw new RunAlreadyClaimedException(
                    id.value(), lease == null ? "nobody" : lease.heldBy(), now);
        }
        this.lease = lease.renewedAt(now, duration);
    }

    /**
     * Whether the executor driving this run has stopped saying it is alive.
     *
     * <p>A run with no lease is not orphaned — it has not been claimed yet.
     */
    public boolean isOrphanedAt(Instant now) {
        return status == RunStatus.RUNNING && lease != null && lease.hasExpiredAt(now);
    }

    /**
     * Records a completed step attempt and charges it to the budget.
     *
     * <p>The charge happens whatever the outcome, including a failure. A failed model call costs
     * the same as a successful one, and a budget that only counted successes would under-report
     * exactly the runs that went worst.
     *
     * <p>Idempotent on {@code (stepId, attempt)}. Redelivery is the normal case once a lease can
     * expire mid-flight: the executor records a step, loses the response, and retries. Without
     * this the retry is charged twice, and a double charge is indistinguishable from a step that
     * genuinely cost twice as much.
     */
    public void record(TraceEntry entry, String executorId) {
        Objects.requireNonNull(entry, "trace entry");
        requireRunning();
        requireLeaseHeldBy(executorId);

        boolean alreadyRecorded = trace.stream()
                .anyMatch(seen -> seen.stepId().equals(entry.stepId())
                        && seen.attempt() == entry.attempt());
        if (alreadyRecorded) {
            return;
        }

        if (entry.sequence() != trace.size()) {
            throw new IllegalArgumentException(
                    ("trace of run %s expects sequence %d, got %d. A gap makes the run "
                                    + "unreplayable and out-of-order entries make it unreadable")
                            .formatted(id, trace.size(), entry.sequence()));
        }
        trace.add(entry);
        outcomes.put(entry.stepId(), entry.outcome());

        // Raised for every step including failures: a failed model call costs the same as a
        // successful one, and a ledger that counted only successes would under-report exactly
        // the runs that went worst.
        domainEvents.add(new StepCostIncurred(
                id.value(),
                tenant.value().toString(),
                entry.stepId(),
                entry.attempt(),
                entry.tokens(),
                entry.costMicros(),
                entry.startedAt()));

        try {
            budget.consume(entry.tokens(), entry.took(), entry.costMicros());
        } catch (BudgetExceededException breach) {
            // Suspend rather than truncate. The entry stays in the trace: it happened, it was
            // paid for, and hiding it would make the ledger disagree with the invoice.
            suspend(breach.getMessage(), entry.startedAt().plus(entry.took()));
        }
    }

    private void requireLeaseHeldBy(String executorId) {
        if (lease == null || !lease.isHeldBy(executorId)) {
            throw new RunAlreadyClaimedException(
                    id.value(), lease == null ? "nobody" : lease.heldBy(), startedAt);
        }
    }

    /**
     * Stops the run at a ceiling, resumable.
     *
     * <p>Raises an event because a suspended run is somebody's work item: it sits there costing
     * nothing and delivering nothing until a person decides whether the question is worth more
     * money. Silence here is how suspended runs become abandoned ones.
     */
    public void suspend(String reason, Instant at) {
        if (status == RunStatus.SUSPENDED) {
            return;
        }
        requireRunning();
        this.status = RunStatus.SUSPENDED;
        this.statusReason = reason;
        domainEvents.add(new RunSuspended(
                id.value(),
                tenant.value().toString(),
                principal.value().toString(),
                question,
                reason,
                at));
    }

    /**
     * Resumes a suspended run under a raised ceiling.
     *
     * <p>Takes a new budget rather than clearing the old one. Resuming under the ceiling that was
     * just breached would suspend again on the next step, and the loop would look like a hang.
     */
    public void resume(RunBudget raised) {
        Objects.requireNonNull(raised, "raised budget");
        if (status != RunStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "run %s is %s and is not waiting to be resumed".formatted(id, status));
        }
        if (raised.fractionConsumed() >= 1.0d) {
            throw new IllegalArgumentException(
                    "the raised budget is already exhausted; the run would suspend again "
                            + "immediately and the loop would present as a hang");
        }
        this.budget = raised;
        this.status = RunStatus.RUNNING;
        this.statusReason = null;
    }

    public void fail(String reason) {
        requireRunning();
        this.status = RunStatus.FAILED;
        this.statusReason = Objects.requireNonNull(reason, "failure reason");
    }

    public void abandon(String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException("run %s is already %s".formatted(id, status));
        }
        this.status = RunStatus.ABANDONED;
        this.statusReason = reason;
    }

    public void complete() {
        requireRunning();
        List<String> unfinished = mandatoryStepIds.stream()
                .filter(step -> !outcomes.getOrDefault(step, StepOutcome.PENDING).isTerminal())
                .toList();
        if (!unfinished.isEmpty()) {
            throw new IllegalStateException(
                    "run %s cannot complete with mandatory steps unfinished: %s"
                            .formatted(id, unfinished));
        }
        this.status = RunStatus.COMPLETED;
    }

    // -- the rule the platform is sold on -----------------------------------

    /**
     * Refuses unless this run is entitled to an artefact.
     *
     * <p>Separate from {@link #emitArtefact} so the check can be made <em>before</em> a
     * deliverable is built. Emitting is the last step of a sequence that also persists rows in
     * another module, and running that sequence first only to reject it at the end would leave
     * an artifact behind for a run that was never allowed one.
     *
     * <p>Refuses when any mandatory step abstained, was skipped or failed. The refusal carries
     * which steps and why, because "no artefact" on its own is not actionable — the user needs to
     * know that the platform could not establish a specific thing, which is itself a useful
     * answer and often the correct one.
     */
    public void requireArtefactReady() {
        if (status != RunStatus.COMPLETED) {
            throw new IllegalStateException(
                    "run %s is %s; only a completed run emits an artefact".formatted(id, status));
        }
        List<String> blocking = blockingSteps();
        if (!blocking.isEmpty()) {
            throw new MandatoryStepAbstainedException(id.value(), blocking, abstentions());
        }
    }

    /**
     * Records the deliverable this run produced.
     *
     * <p>The id is the one returned by the module that persisted the artifact, never one chosen
     * by a caller. An accepted id was only ever a claim that something existed, and a run whose
     * {@code artefactId} points at nothing reads as healthy from every angle: the status is
     * COMPLETED, the field is populated, and the absence shows up when a client asks for the
     * document.
     */
    public String emitArtefact(String persistedArtefactId) {
        requireArtefactReady();
        this.artefactId = Objects.requireNonNull(persistedArtefactId, "artefact id");
        return artefactId;
    }

    /** Mandatory steps that did not conclude. Non-empty means no artefact. */
    public List<String> blockingSteps() {
        return mandatoryStepIds.stream()
                .filter(step -> {
                    StepOutcome outcome = outcomes.getOrDefault(step, StepOutcome.PENDING);
                    return outcome != StepOutcome.CONCLUDED;
                })
                .toList();
    }

    /** Every abstention the run recorded, in order. Surfaced to the user instead of an artefact. */
    public List<Abstention> abstentions() {
        return trace.stream()
                .map(TraceEntry::abstention)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean mayEmitArtefact() {
        return status == RunStatus.COMPLETED && blockingSteps().isEmpty();
    }

    // -- replay -------------------------------------------------------------

    /**
     * Whether this run can be replayed to the same answer.
     *
     * <p>Two requirements, both necessary: the configuration is pinned, and the trace is dense
     * from zero. A trace with a hole cannot be replayed past the hole, and a run whose
     * configuration was not recorded cannot be replayed at all — the model that produced it is
     * unknown.
     */
    public boolean isReplayable() {
        if (trace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trace.size(); i++) {
            if (trace.get(i).sequence() != i) {
                return false;
            }
        }
        return true;
    }

    public long totalCostMicros() {
        return trace.stream().mapToLong(TraceEntry::costMicros).sum();
    }

    public long totalTokens() {
        return trace.stream().mapToLong(TraceEntry::tokens).sum();
    }

    /** Restores persisted state without replaying the transitions that produced it. */
    public void rehydrate(
            RunStatus status,
            String statusReason,
            String artefactId,
            RunLease lease,
            List<TraceEntry> entries) {
        this.status = Objects.requireNonNull(status, "status");
        this.statusReason = statusReason;
        this.artefactId = artefactId;
        this.lease = lease;
        this.trace.clear();
        this.trace.addAll(TraceEntry.ordered(entries));
        this.outcomes.clear();
        this.trace.forEach(entry -> outcomes.put(entry.stepId(), entry.outcome()));
    }

    public RunLease lease() {
        return lease;
    }

    private void requireRunning() {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException(
                    "run %s is %s, not RUNNING".formatted(id, status));
        }
    }

    public RunId id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public PrincipalId principal() {
        return principal;
    }

    public String question() {
        return question;
    }

    public String skillVersionedId() {
        return skillVersionedId;
    }

    public ConfigFingerprint fingerprint() {
        return fingerprint;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public RunStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public String artefactId() {
        return artefactId;
    }

    public List<TraceEntry> trace() {
        return List.copyOf(trace);
    }

    public List<String> mandatoryStepIds() {
        return mandatoryStepIds;
    }

    public Duration elapsedAt(Instant now) {
        return Duration.between(startedAt, now);
    }

    // The ceilings, not the consumption. Persisting what was spent would let a reloaded run
    // start again from zero against the same ceiling, so a suspended run would resume itself.
    public long budgetCeilingTokens() {
        return budget.maxTokens();
    }

    public long budgetCeilingWallMillis() {
        return budget.maxWallClock().toMillis();
    }

    public long budgetCeilingSpendMicros() {
        return budget.maxSpendMicros();
    }

    /** Drained by the application service after the transaction commits. */
    public List<Object> drainEvents() {
        var drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }
}
