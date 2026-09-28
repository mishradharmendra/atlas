-- The measurement, made durable before the thing it measures exists.
--
-- Built ahead of the agent deliberately. A benchmark written after the system it grades
-- is written by people who know what the system currently does, and it encodes that;
-- every later comparison is then against a yardstick shaped by its own subject.

CREATE TABLE eval_rubric (
    rubric_id   VARCHAR(128) NOT NULL,

    -- Revision produces a version, never an edit. The results already reported against
    -- v1 keep the standard they were graded by, which is the only way a trend line over
    -- several quarters means anything.
    version     INTEGER      NOT NULL,

    question    VARCHAR(2048) NOT NULL,
    as_of       TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    frozen_at   TIMESTAMPTZ,

    -- One opaque document rather than a child table. Criteria are only ever read whole,
    -- never change once frozen, and must round-trip byte-identically -- which a single
    -- column makes provable instead of dependent on child-row ordering.
    criteria    TEXT         NOT NULL,

    PRIMARY KEY (rubric_id, version),
    CONSTRAINT eval_rubric_status_check CHECK (status IN ('DRAFT', 'FROZEN')),

    -- A frozen rubric without a freeze timestamp cannot be placed in the history, so a
    -- result graded by it cannot be defended.
    CONSTRAINT eval_rubric_frozen_has_timestamp
        CHECK (status <> 'FROZEN' OR frozen_at IS NOT NULL)
);

CREATE TABLE eval_baseline (
    baseline_id              VARCHAR(128) PRIMARY KEY,
    benchmark                VARCHAR(128) NOT NULL,

    -- Two rubric versions grade differently by construction, so comparing across them
    -- is a change of yardstick wearing the costume of a quality delta.
    rubric_version           INTEGER      NOT NULL,

    pinned_at                TIMESTAMPTZ  NOT NULL,
    metrics                  TEXT         NOT NULL,

    -- Including retrieval-loop tokens. Excluding them makes a system that retrieves five
    -- times per question look identical to one that retrieves once, until the invoice.
    cost_per_question_micros BIGINT       NOT NULL,

    CONSTRAINT eval_baseline_cost_check CHECK (cost_per_question_micros >= 0)
);

CREATE TABLE eval_run (
    run_id                   VARCHAR(128) PRIMARY KEY,
    benchmark                VARCHAR(128) NOT NULL,
    rubric_id                VARCHAR(128) NOT NULL,
    rubric_version           INTEGER      NOT NULL,
    baseline_id              VARCHAR(128) NOT NULL,
    ran_at                   TIMESTAMPTZ  NOT NULL,

    -- Model, prompt version, index version, retrieval params. Without it two runs of
    -- different systems are indistinguishable in the history, and an A/B comparison
    -- reports no delta because both arms read the same cache.
    system_fingerprint       VARCHAR(512) NOT NULL,

    questions                INTEGER      NOT NULL,

    -- Counted apart from questions: an unanswerable question has undefined recall, not
    -- zero, and folding the two together understates exactly the slices that were
    -- deliberately seeded with them.
    scored                   INTEGER      NOT NULL,

    metrics                  TEXT         NOT NULL,
    cost_per_question_micros BIGINT       NOT NULL,

    CONSTRAINT eval_run_rubric_fk
        FOREIGN KEY (rubric_id, rubric_version) REFERENCES eval_rubric (rubric_id, version),
    CONSTRAINT eval_run_baseline_fk
        FOREIGN KEY (baseline_id) REFERENCES eval_baseline (baseline_id),
    CONSTRAINT eval_run_scored_check CHECK (scored >= 0 AND scored <= questions),
    CONSTRAINT eval_run_questions_check CHECK (questions > 0),
    CONSTRAINT eval_run_cost_check CHECK (cost_per_question_micros >= 0)
);

-- The trend line for one benchmark is the query this table exists to answer.
CREATE INDEX eval_run_history ON eval_run (benchmark, ran_at DESC);
