-- Tenancy: who the customer is, what they bought, and who holds a seat.
--
-- Subscriptions and seat assignments both carry validity intervals rather than being
-- overwritten. The naive model stores a seat count on the tenant and updates it; it works
-- until the first billing dispute, which is always about a past period. A count changed in
-- October would retroactively recalculate the September invoice, and the argument is then
-- settled by whoever has better records -- which will not be the vendor whose system
-- overwrites.

CREATE TABLE tenant (
    tenant_id     UUID         PRIMARY KEY,
    name          VARCHAR(256) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    status_reason VARCHAR(1024),

    CONSTRAINT tenant_status_check
        CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CLOSED')),

    -- A suspension nobody can explain cannot be reversed by the person who has to decide.
    CONSTRAINT tenant_suspension_has_reason
        CHECK (status <> 'SUSPENDED' OR status_reason IS NOT NULL)
);

CREATE TABLE tenant_subscription (
    tenant_id        UUID         NOT NULL REFERENCES tenant (tenant_id),
    plan_id          VARCHAR(128) NOT NULL,
    seats            INTEGER      NOT NULL,
    seat_price_micros BIGINT      NOT NULL,
    trial            BOOLEAN      NOT NULL DEFAULT FALSE,
    valid_from       TIMESTAMPTZ  NOT NULL,
    valid_to         TIMESTAMPTZ,

    CONSTRAINT tenant_subscription_window_check
        CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT tenant_subscription_has_seats CHECK (seats >= 1),
    CONSTRAINT tenant_subscription_price_non_negative CHECK (seat_price_micros >= 0),

    -- A trial with a seat price is a paid subscription. Conflating them makes every margin
    -- report treat trials as loss-making customers.
    CONSTRAINT tenant_subscription_trial_is_free
        CHECK (NOT trial OR seat_price_micros = 0),

    -- Two subscriptions covering one instant make the seat count ambiguous, and that count
    -- is the denominator of every invoice and every margin figure.
    CONSTRAINT tenant_subscription_no_overlap
        EXCLUDE USING gist (
            tenant_id WITH =,
            tstzrange(valid_from, valid_to, '[)') WITH &&
        )
);

CREATE TABLE tenant_seat (
    tenant_id    UUID        NOT NULL REFERENCES tenant (tenant_id),
    principal_id UUID        NOT NULL,
    valid_from   TIMESTAMPTZ NOT NULL,
    valid_to     TIMESTAMPTZ,

    CONSTRAINT tenant_seat_window_check
        CHECK (valid_to IS NULL OR valid_to > valid_from),

    -- Two concurrent assignments bill the same person twice and flatter every per-seat
    -- usage figure, which is the direction nobody checks.
    CONSTRAINT tenant_seat_one_per_principal
        EXCLUDE USING gist (
            tenant_id WITH =,
            principal_id WITH =,
            tstzrange(valid_from, valid_to, '[)') WITH &&
        )
);

-- "Who held a seat in September" is the billable fact.
CREATE INDEX tenant_seat_by_window ON tenant_seat (tenant_id, valid_from);
