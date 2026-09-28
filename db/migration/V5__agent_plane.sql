-- The agent plane's durable half: skills and the runs that executed them.
--
-- Two things here are load-bearing beyond ordinary storage:
--
--   1. a run pins the skill VERSION and the config fingerprint, so "why did this answer
--      change?" is answerable without guessing;
--   2. the trace is keyed (run_id, sequence) with a dense sequence, so a replay cannot
--      silently skip a step it has no record of.

CREATE TABLE skill (
    skill_id     VARCHAR(128) NOT NULL,
    version      INTEGER      NOT NULL,
    name         VARCHAR(256) NOT NULL,
    intent       VARCHAR(1024) NOT NULL,

    -- Steps as JSON. They are read as a whole, never queried into: a run loads the entire
    -- procedure or none of it, and normalising them would buy joins nobody performs while
    -- making the ordered list -- which is the whole meaning of a skill -- an emergent
    -- property of an ORDER BY.
    steps        TEXT         NOT NULL,

    frozen       BOOLEAN      NOT NULL DEFAULT FALSE,
    frozen_at    TIMESTAMPTZ,

    PRIMARY KEY (skill_id, version),

    CONSTRAINT skill_version_positive CHECK (version >= 1),

    -- A frozen skill with no freeze instant cannot be ordered against the runs that used it.
    CONSTRAINT skill_frozen_has_instant CHECK (frozen = FALSE OR frozen_at IS NOT NULL)
);

CREATE TABLE research_run (
    run_id            VARCHAR(128) PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    principal_id      UUID         NOT NULL,
    question          VARCHAR(2048) NOT NULL,

    -- 'skill@vN'. Storing the bare skill id would make two runs look comparable when the
    -- procedure between them had changed.
    skill_versioned_id VARCHAR(160) NOT NULL,
    mandatory_steps    VARCHAR(1024) NOT NULL,

    -- model + prompt version + index version + retrieval params, canonically ordered.
    config_fingerprint VARCHAR(1024) NOT NULL,

    started_at        TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    status_reason     VARCHAR(1024),
    artefact_id       VARCHAR(128),

    max_tokens        BIGINT       NOT NULL,
    max_wall_millis   BIGINT       NOT NULL,
    max_spend_micros  BIGINT       NOT NULL,

    CONSTRAINT research_run_status_check
        CHECK (status IN ('PLANNED', 'RUNNING', 'SUSPENDED', 'COMPLETED', 'FAILED', 'ABANDONED')),

    -- The rule the platform is sold on, restated where the application cannot be bypassed:
    -- only a completed run has an artefact.
    CONSTRAINT research_run_artefact_only_when_completed
        CHECK (artefact_id IS NULL OR status = 'COMPLETED'),

    -- A suspended or failed run that does not say why is an unanswerable support ticket.
    CONSTRAINT research_run_stopped_has_reason
        CHECK (status NOT IN ('SUSPENDED', 'FAILED') OR status_reason IS NOT NULL),

    CONSTRAINT research_run_skill_is_versioned
        CHECK (skill_versioned_id LIKE '%@v%')
);

CREATE INDEX research_run_suspended ON research_run (status, started_at);
CREATE INDEX research_run_by_principal ON research_run (principal_id, started_at DESC);

CREATE TABLE research_trace_entry (
    run_id       VARCHAR(128) NOT NULL REFERENCES research_run (run_id) ON DELETE CASCADE,

    -- Dense from zero. The primary key is what stops two workers writing the same position and
    -- what makes "is this trace complete?" a question the database can answer.
    sequence     INTEGER      NOT NULL,

    step_id      VARCHAR(128) NOT NULL,
    attempt      INTEGER      NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    took_millis  BIGINT       NOT NULL,

    action       VARCHAR(4096) NOT NULL,
    observation  VARCHAR(8192) NOT NULL,
    outcome      VARCHAR(16)   NOT NULL,

    abstention_reason   VARCHAR(32),
    abstention_detail   VARCHAR(1024),
    abstention_coverage REAL,

    tokens       BIGINT       NOT NULL,
    cost_micros  BIGINT       NOT NULL,

    PRIMARY KEY (run_id, sequence),

    CONSTRAINT research_trace_outcome_check
        CHECK (outcome IN ('PENDING', 'CONCLUDED', 'ABSTAINED', 'DEGRADED', 'FAILED', 'SKIPPED')),
    CONSTRAINT research_trace_sequence_dense CHECK (sequence >= 0),
    CONSTRAINT research_trace_attempt_positive CHECK (attempt >= 1),

    -- An unexplained abstention cannot be told apart from a crash, and the two need opposite
    -- responses: one is a correct answer, the other an outage.
    CONSTRAINT research_trace_abstention_has_reason
        CHECK (outcome <> 'ABSTAINED' OR abstention_reason IS NOT NULL),
    CONSTRAINT research_trace_reason_only_when_abstained
        CHECK (outcome = 'ABSTAINED' OR abstention_reason IS NULL),

    CONSTRAINT research_trace_cost_not_negative CHECK (tokens >= 0 AND cost_micros >= 0)
);
