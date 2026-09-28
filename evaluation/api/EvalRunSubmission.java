package com.atlas.evaluation.api;

import java.time.Instant;
import java.util.Map;

/**
 * What the compute plane reports after running a benchmark.
 *
 * @param systemFingerprint model, prompt version, index version and retrieval params. Two runs of
 *     different systems are otherwise indistinguishable in the history, and an A/B comparison
 *     shows no delta because both arms read the same cache.
 * @param scored questions that had at least one relevant document. Counted apart from
 *     {@code questions} because an unanswerable question has undefined recall, not zero, and
 *     folding them together understates precisely the slices deliberately seeded with them.
 * @param costPerQuestionMicros including retrieval-loop tokens. Excluding them makes a system that
 *     retrieves five times per question look identical to one that retrieves once.
 */
public record EvalRunSubmission(
        String runId,
        String benchmark,
        String rubricId,
        int rubricVersion,
        String baselineId,
        Instant ranAt,
        String systemFingerprint,
        int questions,
        int scored,
        Map<String, Double> metrics,
        long costPerQuestionMicros) {}
