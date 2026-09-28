package com.atlas.research.api;

/**
 * A run was refused because the platform could not hold one of its skill's guardrails.
 *
 * <p>Distinct from a validation error: the request is well-formed and the skill is legitimate.
 * What is missing is the structural condition that makes the guardrail true, and the remedy is
 * to run under a credential that has it rather than to correct the payload.
 *
 * <p>Refusing is the point. A guardrail the platform cannot enforce is a sentence in a document,
 * and a run that proceeds while declaring one is worse than a run that declares nothing — it
 * produces a deliverable carrying an assurance nobody checked.
 */
public class GuardrailNotSatisfiedException extends RuntimeException {

    public GuardrailNotSatisfiedException(String message) {
        super(message);
    }
}
