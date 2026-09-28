-- The usage ledger.
--
-- Immutable and append-only. An editable ledger is not a ledger: the first time a figure is
-- corrected in place, the history of what was believed is gone and the reconciliation
-- against the provider invoice has nothing to reconcile with. Corrections are compensating
-- records.

CREATE TABLE usage_record (
    -- (run, step, attempt) rather than a surrogate id, so a redelivered event collides
    -- instead of double-counting. A double-counted step is invisible -- indistinguishable
    -- from a step that genuinely cost twice as much.
    run_id       VARCHAR(128) NOT NULL,
    step_id      VARCHAR(128) NOT NULL,
    attempt      INTEGER      NOT NULL,

    tenant_id    UUID         NOT NULL,
    tokens       BIGINT       NOT NULL,
    cost_micros  BIGINT       NOT NULL,
    incurred_at  TIMESTAMPTZ  NOT NULL,

    -- Denormalised from incurred_at so the aggregate query groups on an indexed column
    -- rather than on a function of one.
    period       VARCHAR(7)   NOT NULL,

    PRIMARY KEY (run_id, step_id, attempt),

    CONSTRAINT usage_record_non_negative
        CHECK (tokens >= 0 AND cost_micros >= 0)
);

-- The commercial question: this tenant, this month.
CREATE INDEX usage_record_by_tenant_period ON usage_record (tenant_id, period);

CREATE TABLE budget_policy (
    tenant_id      UUID        NOT NULL,
    period         VARCHAR(7)  NOT NULL,
    ceiling_micros BIGINT      NOT NULL,
    on_breach      VARCHAR(24) NOT NULL,

    PRIMARY KEY (tenant_id, period),

    -- A ceiling of zero refuses everything, which is a suspended subscription rather than a
    -- budget. Conflating them means "we disabled you" and "you hit your limit" arrive as the
    -- same message.
    CONSTRAINT budget_policy_positive_ceiling CHECK (ceiling_micros > 0),
    CONSTRAINT budget_policy_action_check
        CHECK (on_breach IN ('WARN', 'REFUSE_NEW_RUNS'))
);
