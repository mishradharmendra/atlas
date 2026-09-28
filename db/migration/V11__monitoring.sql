-- G6.5 monitoring: watchlists, materiality, and the record of what was deliberately not sent.

CREATE TABLE watchlist (
    watchlist_id      VARCHAR(128) PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    owner_id          UUID         NOT NULL,
    name              VARCHAR(256) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,

    -- The materiality rule, flattened. Null only while a watchlist is being assembled; the
    -- constraint below refuses to let one go active without it.
    quiet_period_secs BIGINT,
    max_per_window    INTEGER,
    window_secs       BIGINT,

    active            BOOLEAN      NOT NULL DEFAULT FALSE,

    -- An active watchlist with no rule is a subscription to everything, which is the setting
    -- users mute rather than tune. Refused here as well as in the aggregate because a
    -- configuration import is exactly how a list would come to be active without one.
    CONSTRAINT watchlist_active_has_a_rule
        CHECK (NOT active OR (max_per_window IS NOT NULL AND window_secs IS NOT NULL)),

    CONSTRAINT watchlist_cap_is_positive
        CHECK (max_per_window IS NULL OR max_per_window >= 1),

    CONSTRAINT watchlist_window_is_positive
        CHECK (window_secs IS NULL OR window_secs > 0)
);

CREATE TABLE watchlist_subject (
    watchlist_id VARCHAR(128) NOT NULL REFERENCES watchlist (watchlist_id) ON DELETE CASCADE,
    subject_id   VARCHAR(128) NOT NULL,

    PRIMARY KEY (watchlist_id, subject_id)
);

-- The seek the fan-out listener does on every verified edge.
CREATE INDEX watchlist_subject_by_subject ON watchlist_subject (subject_id);

CREATE TABLE watchlist_relationship_type (
    watchlist_id      VARCHAR(128) NOT NULL REFERENCES watchlist (watchlist_id) ON DELETE CASCADE,
    relationship_type VARCHAR(128) NOT NULL,

    PRIMARY KEY (watchlist_id, relationship_type)
);

CREATE TABLE monitoring_trigger (
    trigger_id         VARCHAR(128) PRIMARY KEY,
    watchlist_id       VARCHAR(128) NOT NULL REFERENCES watchlist (watchlist_id) ON DELETE CASCADE,
    tenant_id          UUID         NOT NULL,
    subject_id         VARCHAR(128) NOT NULL,
    change_kind        VARCHAR(128) NOT NULL,

    -- Identifies the fact, not the document that reported it.
    fact_key           VARCHAR(512) NOT NULL,

    summary            VARCHAR(2048),

    -- The instant the change was observed, not the instant it was processed. A replayed backfill
    -- must not produce a week of alerts that all look like they happened this morning.
    observed_at        TIMESTAMPTZ  NOT NULL,

    status             VARCHAR(16)  NOT NULL,
    suppression_reason VARCHAR(32),
    delivered_at       TIMESTAMPTZ,

    CONSTRAINT trigger_status_check
        CHECK (status IN ('PENDING', 'DELIVERED', 'SUPPRESSED')),

    CONSTRAINT trigger_suppression_reason_check
        CHECK (suppression_reason IS NULL
               OR suppression_reason IN ('DUPLICATE', 'QUIET_PERIOD', 'FANNED_OUT', 'IMMATERIAL')),

    -- A suppressed trigger with no reason is a silent drop wearing a status. The only question
    -- ever asked of a monitoring system is why it stayed quiet.
    CONSTRAINT trigger_suppressed_says_why
        CHECK (status <> 'SUPPRESSED' OR suppression_reason IS NOT NULL),

    CONSTRAINT trigger_delivered_has_timestamp
        CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL)
);

-- Deduplication, enforced rather than checked. The listener also looks before inserting, but the
-- outbox delivers at least once and two concurrent deliveries both pass a look-then-insert.
CREATE UNIQUE INDEX trigger_one_per_fact ON monitoring_trigger (watchlist_id, fact_key);

-- The quiet-period lookup.
CREATE INDEX trigger_by_subject_kind
    ON monitoring_trigger (watchlist_id, subject_id, change_kind, observed_at DESC);

-- The fan-out count, which reads delivered rows only.
CREATE INDEX trigger_delivered_window
    ON monitoring_trigger (watchlist_id, observed_at DESC) WHERE status = 'DELIVERED';
