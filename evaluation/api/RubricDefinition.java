package com.atlas.evaluation.api;

import java.time.Instant;
import java.util.List;

/**
 * A rubric being published: authored and closed in one step.
 *
 * <p>One step rather than create-then-freeze, because a rubric that can sit on the server in a
 * draft state is a rubric somebody can grade against before it is closed — and the result of that
 * grading is unfalsifiable, since the standard can still be edited afterwards.
 */
public record RubricDefinition(
        String rubricId,
        int version,
        String question,
        Instant asOf,
        List<CriterionDefinition> criteria) {

    public record CriterionDefinition(String id, String axis, String statement, int weight) {}
}
