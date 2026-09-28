package com.atlas.skill.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One step of a skill: what it needs, what it must produce, and what happens when it does not.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Every step declares its feedback.</b> See {@link FeedbackSpec}.
 *   <li><b>A mandatory step may not be {@link OnFail#CONTINUE_DEGRADED}.</b> "This step is
 *       required" and "carry on without it" are a contradiction, and the combination reads as
 *       safe in a config file while producing an artefact that silently lacks the thing the
 *       author marked as required.
 *   <li><b>{@link OnFail#RETRY} needs an attempt limit above one</b>, and anything else needs
 *       exactly one. An unbounded retry turns one bad prompt into a rate-limit incident.
 *   <li><b>A step that produces text carries {@link Guardrail#REQUIRE_CITATIONS}.</b> Enforced
 *       here rather than trusted, because an uncited sentence is indistinguishable from a cited
 *       one at the point a client reads it.
 * </ol>
 */
public record StepSpec(
        String stepId,
        String description,
        String taskType,
        boolean mandatory,
        boolean producesProse,
        Set<Condition> preconditions,
        Set<Condition> postconditions,
        Set<Guardrail> guardrails,
        OnFail onFail,
        int maxAttempts,
        FeedbackSpec feedback) {

    public StepSpec {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("step id must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "a step must say what it is for; a trace of unnamed steps cannot be read by "
                            + "the person debugging a run six months from now");
        }
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException(
                    "a step must name its task type, never a model; naming a model here would put "
                            + "a routing decision inside a business artefact");
        }
        Objects.requireNonNull(onFail, "on-fail policy");
        Objects.requireNonNull(feedback, "feedback spec");

        preconditions = Set.copyOf(preconditions);
        postconditions = Set.copyOf(postconditions);
        guardrails = Set.copyOf(guardrails);

        if (mandatory && onFail == OnFail.CONTINUE_DEGRADED) {
            throw new IllegalArgumentException(
                    ("step '%s' is mandatory and set to CONTINUE_DEGRADED. Those contradict: the "
                                    + "run would emit an artefact missing the thing the author "
                                    + "marked as required, and nothing downstream could tell")
                            .formatted(stepId));
        }
        if (onFail == OnFail.RETRY && maxAttempts < 2) {
            throw new IllegalArgumentException(
                    "step '%s' retries but allows %d attempt(s)".formatted(stepId, maxAttempts));
        }
        if (onFail != OnFail.RETRY && maxAttempts != 1) {
            throw new IllegalArgumentException(
                    "step '%s' does not retry, so it must allow exactly one attempt"
                            .formatted(stepId));
        }
        if (maxAttempts > MAX_ATTEMPT_CEILING) {
            throw new IllegalArgumentException(
                    ("step '%s' allows %d attempts; above %d a deterministic failure becomes a "
                                    + "rate-limit incident rather than an error")
                            .formatted(stepId, maxAttempts, MAX_ATTEMPT_CEILING));
        }
        if (producesProse && !guardrails.contains(Guardrail.REQUIRE_CITATIONS)) {
            throw new IllegalArgumentException(
                    ("step '%s' produces prose without REQUIRE_CITATIONS. An uncited sentence is "
                                    + "indistinguishable from a cited one at the point a client "
                                    + "reads it")
                            .formatted(stepId));
        }
    }

    private static final int MAX_ATTEMPT_CEILING = 5;

    /** Whether this step is allowed to leave the run without a conclusion. */
    public boolean mayAbstain() {
        return onFail == OnFail.ABSTAIN || onFail == OnFail.RETRY;
    }

    public List<Condition> orderedPreconditions() {
        return preconditions.stream().sorted().toList();
    }
}
