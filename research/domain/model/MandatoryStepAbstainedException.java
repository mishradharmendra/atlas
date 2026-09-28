package com.atlas.research.domain.model;

import com.atlas.shared.outcome.Abstention;
import java.util.List;

/**
 * Raised instead of producing a deliverable from steps that could not establish their conclusions.
 *
 * <p>Carries the blocking steps and the abstentions rather than a bare message, because "no
 * artefact" is not an answer and this exception is the only thing the user will see. "I could not
 * establish the FY24 restructuring charge because the filing does not disclose it" is a correct,
 * final, useful answer; a generic failure is an outage.
 */
public class MandatoryStepAbstainedException extends IllegalStateException {

    private final transient List<String> blockingSteps;
    private final transient List<Abstention> abstentions;

    public MandatoryStepAbstainedException(
            String runId, List<String> blockingSteps, List<Abstention> abstentions) {
        super("run %s cannot emit an artefact: mandatory step(s) %s did not conclude"
                .formatted(runId, blockingSteps));
        this.blockingSteps = List.copyOf(blockingSteps);
        this.abstentions = List.copyOf(abstentions);
    }

    public List<String> blockingSteps() {
        return blockingSteps;
    }

    public List<Abstention> abstentions() {
        return abstentions;
    }
}
