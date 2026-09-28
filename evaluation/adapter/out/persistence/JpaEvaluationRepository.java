package com.atlas.evaluation.adapter.out.persistence;

import com.atlas.evaluation.domain.model.Baseline;
import com.atlas.evaluation.domain.model.BaselineId;
import com.atlas.evaluation.domain.model.EvalRun;
import com.atlas.evaluation.domain.model.EvalRunId;
import com.atlas.evaluation.domain.model.RubricId;
import com.atlas.evaluation.domain.port.BaselineRepository;
import com.atlas.evaluation.domain.port.EvalRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Entity
@Table(name = "eval_baseline")
class BaselineRow {

    @Id
    @Column(name = "baseline_id", nullable = false, length = 128)
    String baselineId;

    @Column(name = "benchmark", nullable = false, length = 128)
    String benchmark;

    @Column(name = "rubric_version", nullable = false)
    int rubricVersion;

    @Column(name = "pinned_at", nullable = false)
    Instant pinnedAt;

    @Column(name = "metrics", nullable = false, length = 65536)
    String metrics;

    @Column(name = "cost_per_question_micros", nullable = false)
    long costPerQuestionMicros;

    protected BaselineRow() {}
}

@Entity
@Table(name = "eval_run")
class EvalRunRow {

    @Id
    @Column(name = "run_id", nullable = false, length = 128)
    String runId;

    @Column(name = "benchmark", nullable = false, length = 128)
    String benchmark;

    @Column(name = "rubric_id", nullable = false, length = 128)
    String rubricId;

    @Column(name = "rubric_version", nullable = false)
    int rubricVersion;

    @Column(name = "baseline_id", nullable = false, length = 128)
    String baselineId;

    @Column(name = "ran_at", nullable = false)
    Instant ranAt;

    @Column(name = "system_fingerprint", nullable = false, length = 512)
    String systemFingerprint;

    @Column(name = "questions", nullable = false)
    int questions;

    @Column(name = "scored", nullable = false)
    int scored;

    @Column(name = "metrics", nullable = false, length = 65536)
    String metrics;

    @Column(name = "cost_per_question_micros", nullable = false)
    long costPerQuestionMicros;

    protected EvalRunRow() {}
}

interface BaselineJpaRepository extends JpaRepository<BaselineRow, String> {}

interface EvalRunJpaRepository extends JpaRepository<EvalRunRow, String> {}

/** Driven adapter for baselines and runs. */
@Repository
class JpaEvaluationRepository implements BaselineRepository, EvalRunRepository {

    /** A storage format, not the application's wire format. See {@code JpaRubricRepository}. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BaselineJpaRepository baselines;
    private final EvalRunJpaRepository runs;

    JpaEvaluationRepository(BaselineJpaRepository baselines, EvalRunJpaRepository runs) {
        this.baselines = baselines;
        this.runs = runs;
    }

    @Override
    public Optional<Baseline> find(BaselineId id) {
        return baselines.findById(id.value())
                .map(row -> new Baseline(
                        BaselineId.of(row.baselineId),
                        row.benchmark,
                        row.rubricVersion,
                        row.pinnedAt,
                        readMetrics(row.metrics),
                        row.costPerQuestionMicros));
    }

    @Override
    public void pin(Baseline baseline) {
        if (baselines.existsById(baseline.id().value())) {
            // Overwriting in place is how a gate becomes a record of what the system does rather
            // than of what it must do: the number moves to wherever the current build sits, and
            // every future comparison is against that.
            throw new IllegalStateException(
                    ("baseline %s is already pinned; pin a new id rather than moving this one, "
                                    + "or every comparison since it was set becomes unreproducible")
                            .formatted(baseline.id()));
        }
        BaselineRow row = new BaselineRow();
        row.baselineId = baseline.id().value();
        row.benchmark = baseline.benchmark();
        row.rubricVersion = baseline.rubricVersion();
        row.pinnedAt = baseline.pinnedAt();
        row.metrics = writeMetrics(baseline.metrics());
        row.costPerQuestionMicros = baseline.costPerQuestionMicros();
        baselines.save(row);
    }

    @Override
    public Optional<EvalRun> find(EvalRunId id) {
        return runs.findById(id.value())
                .map(row -> new EvalRun(
                        EvalRunId.of(row.runId),
                        row.benchmark,
                        RubricId.of(row.rubricId),
                        row.rubricVersion,
                        BaselineId.of(row.baselineId),
                        row.ranAt,
                        row.systemFingerprint,
                        row.questions,
                        row.scored,
                        readMetrics(row.metrics),
                        row.costPerQuestionMicros));
    }

    @Override
    public void save(EvalRun run) {
        EvalRunRow row = runs.findById(run.id().value()).orElseGet(EvalRunRow::new);
        row.runId = run.id().value();
        row.benchmark = run.benchmark();
        row.rubricId = run.rubricId().value();
        row.rubricVersion = run.rubricVersion();
        row.baselineId = run.baselineId().value();
        row.ranAt = run.ranAt();
        row.systemFingerprint = run.systemFingerprint();
        row.questions = run.questions();
        row.scored = run.scored();
        row.metrics = writeMetrics(run.metrics());
        row.costPerQuestionMicros = run.costPerQuestionMicros();
        runs.save(row);
    }

    private String writeMetrics(Map<String, Double> metrics) {
        try {
            return JSON.writeValueAsString(metrics);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise metrics", e);
        }
    }

    private Map<String, Double> readMetrics(String raw) {
        try {
            return JSON.readValue(raw, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("could not read metrics", e);
        }
    }
}
