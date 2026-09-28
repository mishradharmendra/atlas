package com.atlas.skill.api;

import java.util.List;
import java.util.Set;

/**
 * A skill being authored and frozen in one call.
 *
 * <p>One call rather than create-then-freeze, for the same reason a rubric is published that way:
 * a skill that can sit on the server in draft is a skill somebody runs against before it is
 * closed, and the results of that run describe a procedure that can still change.
 */
public record PublishSkillCommand(
        String skillId, int version, String name, String intent, List<StepDefinition> steps) {

    public PublishSkillCommand {
        steps = List.copyOf(steps);
    }

    /** The wire-facing shape of a step. Flat strings; the domain type does the validating. */
    public record StepDefinition(
            String stepId,
            String description,
            String taskType,
            boolean mandatory,
            boolean producesProse,
            Set<String> preconditions,
            Set<String> postconditions,
            Set<String> guardrails,
            String onFail,
            int maxAttempts,
            String rubricId,
            String feedbackKind,
            boolean blocking) {

        public StepDefinition {
            preconditions = Set.copyOf(preconditions);
            postconditions = Set.copyOf(postconditions);
            guardrails = Set.copyOf(guardrails);
        }
    }
}
