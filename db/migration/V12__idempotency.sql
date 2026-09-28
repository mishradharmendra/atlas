-- G6.6 idempotency keys: making a retried POST safe.
--
-- Every create endpoint in this platform mints a fresh identifier per call, so a client that
-- times out and retries gets a second run, a second prediction or a second watchlist. For
-- metering that is money, and the duplicate is indistinguishable from genuine activity.

CREATE TABLE idempotency_record (
    -- Scoped to the tenant, not global. A shared key namespace lets one tenant present another's
    -- key and be handed their response, which turns a reliability feature into a data leak.
    tenant_id           UUID         NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,

    -- Hash of method, path and body. The same key with a different request is a client bug, and
    -- replaying the first response for it would answer a question nobody asked.
    request_fingerprint VARCHAR(128) NOT NULL,

    response_status     INTEGER      NOT NULL,
    response_body       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (tenant_id, idempotency_key),

    -- Only successful responses are recorded. Storing a 500 would make a transient failure
    -- permanent for that key: the client retries, which is the correct thing to do, and is
    -- handed the same error forever.
    CONSTRAINT idempotency_only_successes
        CHECK (response_status BETWEEN 200 AND 299)
);

-- The sweep that expires old keys. Records are not kept forever: a key is a promise about a
-- retry window, not an audit log, and an unbounded table makes every insert slower.
CREATE INDEX idempotency_by_age ON idempotency_record (created_at);
