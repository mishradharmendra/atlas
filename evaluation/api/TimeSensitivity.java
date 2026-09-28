package com.atlas.evaluation.api;

/** How fast the right answer to this question goes stale. */
public enum TimeSensitivity {

    /** Historical fact. A 2019 filing answers it as well today as it did in 2019. */
    STABLE,

    /** Moves with the reporting cycle. Last quarter's answer is a different answer. */
    PERIODIC,

    /** Moves with events. An answer a week old may already be wrong and will not look it. */
    VOLATILE
}
