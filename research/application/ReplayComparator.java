package com.atlas.research.application;

import com.atlas.research.api.ReplayReport;
import com.atlas.research.domain.model.Run;
import com.atlas.research.domain.model.StepOutcome;
import com.atlas.research.domain.model.TraceEntry;
import com.atlas.shared.replay.ConfigFingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Compares a recorded run against what happens now, and explains the difference.
 *
 * <h2>Why divergence is explained rather than judged</h2>
 *
 * <p>A replay that produces a different answer is usually correct behaviour: models are
 * stochastic, indexes are rebuilt, prompts are improved. A system that reported every difference
 * as a failure would cry wolf until people stopped looking, which is worse than not checking.
 *
 * <p>So the output distinguishes two cases that look identical in a boolean. A divergence under a
 * <em>changed</em> fingerprint is expected and its explanation is the change. A divergence under
 * an <em>identical</em> fingerprint is either non-determinism nobody accounted for or a genuine
 * defect — and that one is worth waking someone for.
 *
 * <p>Executes nothing. The replay is performed by the compute plane and its outcomes are passed
 * in; a domain service that called models would collapse the plane split that keeps model routing
 * out of the domain's release cadence.
 */
@Service
class ReplayComparator {

    ReplayReport compare(
            Run run, String currentModel, String currentIndexVersion, List<String> observed) {
        List<TraceEntry> recorded = run.trace();

        ConfigFingerprint now = run.fingerprint()
                .with(ConfigFingerprint.MODEL, currentModel)
                .with(ConfigFingerprint.INDEX_VERSION, currentIndexVersion);
        SortedMap<String, String> changed = new TreeMap<>(now.differenceFrom(run.fingerprint()));

        List<ReplayReport.Divergence> divergences = new ArrayList<>();
        int compared = Math.min(recorded.size(), observed.size());

        for (int i = 0; i < compared; i++) {
            TraceEntry was = recorded.get(i);
            String outcome = observed.get(i);
            if (!was.outcome().name().equals(outcome)) {
                divergences.add(new ReplayReport.Divergence(
                        was.sequence(), was.stepId(), was.outcome().name(), outcome, noteFor(was, outcome)));
            }
        }

        // A replay that stopped early is a divergence too, and the easiest one to miss: comparing
        // only the overlap makes a run that died at step two look like a perfect reproduction of
        // its first two steps.
        if (observed.size() < recorded.size()) {
            for (int i = compared; i < recorded.size(); i++) {
                TraceEntry missing = recorded.get(i);
                divergences.add(new ReplayReport.Divergence(
                        missing.sequence(),
                        missing.stepId(),
                        missing.outcome().name(),
                        "NOT_REACHED",
                        "the replay stopped before this step"));
            }
        } else if (observed.size() > recorded.size()) {
            for (int i = compared; i < observed.size(); i++) {
                divergences.add(new ReplayReport.Divergence(
                        i, "unknown", "NOT_RECORDED", observed.get(i), "the replay ran extra steps"));
            }
        }

        return new ReplayReport(
                run.id().value(),
                divergences.isEmpty() && run.isReplayable(),
                compared,
                divergences,
                changed);
    }

    /**
     * The sentence that saves the reader a trace diff.
     *
     * <p>An abstention appearing where a conclusion used to be is the direction worth calling out:
     * it usually means retrieval stopped finding something, and it is the shape of regression a
     * quality average hides because the abstention scores as neither right nor wrong.
     */
    private static String noteFor(TraceEntry was, String now) {
        if (was.outcome() == StepOutcome.CONCLUDED && StepOutcome.ABSTAINED.name().equals(now)) {
            return "concluded before, abstains now — evidence that was found is no longer found";
        }
        if (was.outcome() == StepOutcome.ABSTAINED && StepOutcome.CONCLUDED.name().equals(now)) {
            return "abstained before, concludes now — check the conclusion is supported, not merely produced";
        }
        return "outcome changed";
    }
}
