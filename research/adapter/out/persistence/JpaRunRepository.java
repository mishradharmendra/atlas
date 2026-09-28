package com.atlas.research.adapter.out.persistence;

import com.atlas.research.domain.model.Run;
import com.atlas.research.domain.model.RunId;
import com.atlas.research.domain.model.RunStatus;
import com.atlas.research.domain.model.StepOutcome;
import com.atlas.research.domain.model.TraceEntry;
import com.atlas.research.domain.port.RunRepository;
import com.atlas.shared.budget.RunBudget;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.outcome.Abstention;
import com.atlas.shared.outcome.AbstentionReason;
import com.atlas.shared.replay.ConfigFingerprint;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface RunJpaRepository extends JpaRepository<RunRow, String> {
    List<RunRow> findByStatusOrderByStartedAtAsc(String status, Limit limit);

    /** RUNNING rows whose lease has lapsed, oldest lapse first so the worst orphan surfaces. */
    @org.springframework.data.jpa.repository.Query("""
            select r from RunRow r
            where r.status = 'RUNNING'
              and r.leaseExpiresAt is not null
              and r.leaseExpiresAt <= :now
            order by r.leaseExpiresAt asc
            """)
    List<RunRow> findOrphaned(
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            Limit limit);
}

interface TraceEntryJpaRepository extends JpaRepository<TraceEntryRow, TraceEntryRow.Key> {
    List<TraceEntryRow> findByRunIdOrderBySequenceAsc(String runId);
}

/**
 * Driven adapter for runs.
 *
 * <p>The trace is written append-only: entries already persisted are never rewritten, only new
 * ones added. That is not an optimisation. A trace that can be rewritten is not evidence of
 * anything, and the trace is what a run is replayed from and what an auditor is shown.
 */
@Repository
class JpaRunRepository implements RunRepository {

    private final RunJpaRepository runs;
    private final TraceEntryJpaRepository entries;

    JpaRunRepository(RunJpaRepository runs, TraceEntryJpaRepository entries) {
        this.runs = runs;
        this.entries = entries;
    }

    @Override
    public Optional<Run> findById(RunId id) {
        return runs.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Run> suspended(int limit) {
        return runs.findByStatusOrderByStartedAtAsc(RunStatus.SUSPENDED.name(), Limit.of(limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Run> orphaned(java.time.Instant now, int limit) {
        return runs.findOrphaned(now, Limit.of(limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(Run run) {
        RunRow row = runs.findById(run.id().value()).orElseGet(RunRow::new);
        row.runId = run.id().value();
        row.tenantId = run.tenant().value();
        row.principalId = run.principal().value();
        row.question = run.question();
        row.skillVersionedId = run.skillVersionedId();
        row.mandatorySteps = String.join(",", run.mandatoryStepIds());
        row.configFingerprint = run.fingerprint().value();
        row.startedAt = run.startedAt();
        row.status = run.status().name();
        row.statusReason = run.statusReason();
        row.artefactId = run.artefactId();
        row.maxTokens = run.budgetCeilingTokens();
        row.maxWallMillis = run.budgetCeilingWallMillis();
        row.maxSpendMicros = run.budgetCeilingSpendMicros();
        row.leaseHeldBy = run.lease() == null ? null : run.lease().heldBy();
        row.leaseExpiresAt = run.lease() == null ? null : run.lease().expiresAt();
        runs.save(row);

        int persisted = entries.findByRunIdOrderBySequenceAsc(run.id().value()).size();
        for (TraceEntry entry : run.trace()) {
            if (entry.sequence() < persisted) {
                continue; // append-only: an entry already written is never rewritten
            }
            entries.save(toRow(run.id().value(), entry));
        }
    }

    private static TraceEntryRow toRow(String runId, TraceEntry entry) {
        TraceEntryRow row = new TraceEntryRow();
        row.runId = runId;
        row.sequence = entry.sequence();
        row.stepId = entry.stepId();
        row.attempt = entry.attempt();
        row.startedAt = entry.startedAt();
        row.tookMillis = entry.took().toMillis();
        row.action = entry.action();
        row.observation = entry.observation();
        row.outcome = entry.outcome().name();
        if (entry.abstention() != null) {
            row.abstentionReason = entry.abstention().reason().name();
            row.abstentionDetail = entry.abstention().detail();
            row.abstentionCoverage = entry.abstention().coverageEstimate();
        }
        row.tokens = entry.tokens();
        row.costMicros = entry.costMicros();
        return row;
    }

    private Run toDomain(RunRow row) {
        var components = new TreeMap<String, String>();
        Arrays.stream(row.configFingerprint.split(";"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .forEach(pair -> components.put(pair[0], pair[1]));

        Run run = new Run(
                RunId.of(row.runId),
                new TenantId(row.tenantId),
                new PrincipalId(row.principalId),
                row.question,
                row.skillVersionedId,
                List.of(row.mandatorySteps.split(",")),
                new ConfigFingerprint(components),
                row.startedAt,
                new RunBudget(
                        row.maxTokens, Duration.ofMillis(row.maxWallMillis), row.maxSpendMicros));

        run.rehydrate(
                RunStatus.valueOf(row.status),
                row.statusReason,
                row.artefactId,
                row.leaseHeldBy == null
                        ? null
                        : new com.atlas.research.domain.model.RunLease(
                                row.leaseHeldBy, row.leaseExpiresAt),
                entries.findByRunIdOrderBySequenceAsc(row.runId).stream()
                        .map(JpaRunRepository::toEntry)
                        .toList());
        return run;
    }

    private static TraceEntry toEntry(TraceEntryRow row) {
        Abstention abstention = row.abstentionReason == null
                ? null
                : new Abstention(
                        row.stepId,
                        AbstentionReason.valueOf(row.abstentionReason),
                        row.abstentionDetail,
                        row.abstentionCoverage == null ? 0f : row.abstentionCoverage);

        return new TraceEntry(
                row.sequence,
                row.stepId,
                row.attempt,
                row.startedAt,
                Duration.ofMillis(row.tookMillis),
                row.action,
                row.observation,
                StepOutcome.valueOf(row.outcome),
                abstention,
                row.tokens,
                row.costMicros);
    }
}
