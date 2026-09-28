package com.atlas.research.api;

import java.util.List;

/**
 * What the caller is told when a run refuses to produce a deliverable.
 *
 * <p>Carries the abstentions rather than a message, because this response *is* the answer. An
 * analyst told "the filing does not disclose the FY24 restructuring charge" has been given
 * something true and final; one told "could not generate artefact" has been given an error.
 *
 * <p>{@code groundTruth} separates the two cases that matter: the corpus genuinely lacks the
 * answer, versus the run hit an operational limit. Presenting the second as the first states as
 * fact that something does not exist when it may.
 */
public record MandatoryStepAbstainedResponse(
        String message, List<String> blockingSteps, List<Reason> reasons) {

    public MandatoryStepAbstainedResponse {
        blockingSteps = List.copyOf(blockingSteps);
        reasons = List.copyOf(reasons);
    }

    public record Reason(String stepId, String reason, String detail, boolean groundTruth) {}
}
