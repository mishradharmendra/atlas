package com.atlas.skill.domain.model;

/**
 * A rule the platform enforces around a step, regardless of what the step or the model does.
 *
 * <p>Guardrails are declared on the skill and applied by the engine, not implemented inside step
 * code. A guardrail a step enforces on itself is a guardrail the next step's author forgets, and
 * one enforced by a prompt is a guardrail that a sufficiently unusual input removes.
 */
public enum Guardrail {

    /**
     * Retrieved text is fenced and treated as data, never as instructions.
     *
     * <p>The corpus is adversarial by default: filings, transcripts and broker notes are written
     * by third parties, and one of them containing "ignore previous instructions" is a question
     * of time rather than of likelihood.
     */
    TREAT_RETRIEVED_TEXT_AS_DATA,

    /** Quotation stays inside the source contract's character ceiling. */
    RESPECT_QUOTE_CEILING,

    /** Every factual sentence carries a citation that resolves to a span. */
    REQUIRE_CITATIONS,

    /**
     * Tenant-private content never leaves the tenant, including into a model that trains on it.
     */
    NO_CROSS_TENANT_EGRESS,

    /**
     * Material non-public information is never mixed into a public-facing artefact.
     *
     * <p>Separate from the entitlement check because the failure is different: entitlement asks
     * whether this principal may see it, this asks whether it may be combined with that.
     */
    NO_MNPI_COMMINGLING,

    /**
     * The step's output is checked by a model of a different family before it is accepted.
     *
     * <p>Same rule as the graph's: same-family review measures determinism, not correctness.
     */
    CROSS_FAMILY_CRITIQUE
}
