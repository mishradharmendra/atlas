package com.atlas.evaluation.application;

import com.atlas.evaluation.api.EvalRunSubmission;
import com.atlas.evaluation.api.EvaluationApi;
import com.atlas.evaluation.api.RubricDefinition;
import com.atlas.evaluation.domain.model.Axis;
import com.atlas.evaluation.domain.model.Baseline;
import com.atlas.evaluation.domain.model.BaselineId;
import com.atlas.evaluation.domain.model.EvalRun;
import com.atlas.evaluation.domain.model.EvalRunId;
import com.atlas.evaluation.domain.model.Rubric;
import com.atlas.evaluation.domain.model.RubricCriterion;
import com.atlas.evaluation.domain.model.RubricId;
import com.atlas.evaluation.domain.model.Weight;
import com.atlas.evaluation.domain.port.BaselineRepository;
import com.atlas.evaluation.domain.port.EvalRunRepository;
import com.atlas.evaluation.domain.port.RubricRepository;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admits benchmark results and gates them against the pinned reference point. */
@Service
class EvaluationService implements EvaluationApi {

    private final RubricRepository rubrics;
    private final BaselineRepository baselines;
    private final EvalRunRepository runs;
    private final Clock clock;
    private final double tolerance;

    EvaluationService(
            RubricRepository rubrics,
            BaselineRepository baselines,
            EvalRunRepository runs,
            Clock clock,
            @Value("${atlas.evaluation.slice-tolerance:0.02}") double tolerance) {
        this.rubrics = rubrics;
        this.baselines = baselines;
        this.runs = runs;
        this.clock = clock;
        this.tolerance = tolerance;
    }

    @Override
    @Transactional
    public int publishRubric(RubricDefinition definition) {
        RubricId id = RubricId.of(definition.rubricId());

        var existing = rubrics.find(id, definition.version());
        if (existing.isPresent()) {
            // The publish step runs on every CI build. Failing the second one would make it a
            // step people comment out, and then nothing is frozen at all.
            Rubric already = existing.get();
            if (!sameCriteria(already, definition)) {
                throw new IllegalStateException(
                        ("rubric %s v%d is already published with different criteria; publish "
                                        + "v%d instead, or every number reported under this "
                                        + "version becomes unreproducible")
                                .formatted(id, definition.version(), definition.version() + 1));
            }
            return already.loadBearingSharePercent();
        }

        Rubric rubric = new Rubric(id, definition.question(), definition.asOf(), definition.version());
        for (var criterion : definition.criteria()) {
            rubric.addCriterion(new RubricCriterion(
                    criterion.id(),
                    Axis.valueOf(criterion.axis()),
                    criterion.statement(),
                    Weight.of(criterion.weight())));
        }
        rubric.freeze(clock.instant());
        rubrics.save(rubric);
        return rubric.loadBearingSharePercent();
    }

    private static boolean sameCriteria(Rubric existing, RubricDefinition definition) {
        var published = existing.criteria().stream()
                .map(c -> "%s|%s|%s|%d".formatted(c.id(), c.axis(), c.statement(), c.weight().value()))
                .sorted()
                .toList();
        var offered = definition.criteria().stream()
                .map(c -> "%s|%s|%s|%d".formatted(c.id(), c.axis(), c.statement(), c.weight()))
                .sorted()
                .toList();
        return published.equals(offered);
    }

    @Override
    @Transactional
    public List<String> recordRun(EvalRunSubmission submission) {
        Rubric rubric = rubrics.find(RubricId.of(submission.rubricId()), submission.rubricVersion())
                .orElseThrow(() -> new NoSuchElementException(
                        "no rubric %s v%d".formatted(submission.rubricId(), submission.rubricVersion())));

        if (!rubric.isFrozen()) {
            // A result graded by a draft has a standard that can still change afterwards, which
            // makes the number unfalsifiable rather than provisional.
            throw new IllegalStateException(
                    ("rubric %s v%d is still a draft; a result graded by it could be revalidated "
                                    + "by editing the rubric")
                            .formatted(rubric.id(), rubric.version()));
        }

        Baseline baseline = baselines.find(BaselineId.of(submission.baselineId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "no baseline " + submission.baselineId()));

        EvalRun run = new EvalRun(
                EvalRunId.of(submission.runId()),
                submission.benchmark(),
                rubric.id(),
                rubric.version(),
                baseline.id(),
                submission.ranAt(),
                submission.systemFingerprint(),
                submission.questions(),
                submission.scored(),
                submission.metrics(),
                submission.costPerQuestionMicros());

        // Recorded before gating. A failing run is the one most worth keeping: deleting it leaves
        // a history in which quality only ever improved.
        runs.save(run);

        return run.regressionsAgainst(baseline, tolerance).stream()
                .map(EvalRun.Regression::toString)
                .toList();
    }

    @Override
    @Transactional
    public void pinBaseline(String baselineId, String evalRunId) {
        EvalRun run = runs.find(EvalRunId.of(evalRunId))
                .orElseThrow(() -> new NoSuchElementException("no run " + evalRunId));

        baselines.pin(new Baseline(
                BaselineId.of(baselineId),
                run.benchmark(),
                run.rubricVersion(),
                clock.instant(),
                run.metrics(),
                run.costPerQuestionMicros()));
    }
}
