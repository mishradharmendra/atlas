-- The provenance spine's durable half.
--
-- Until these tables existed the catalog was model-only: a Document aggregate with
-- invariants and lifecycle events that nothing could construct or persist. Which meant
-- DocumentRetracted -- the event the entire takedown cascade hangs off -- could never
-- fire, because there was no document to retract.

CREATE TABLE source_contract (
    source_id         VARCHAR(128) PRIMARY KEY,
    contract_id       VARCHAR(128),

    -- Comma-separated Use names rather than a boolean column per use. Adding a seventh
    -- use needs no migration, and a use the running code does not recognise round-trips
    -- instead of being silently dropped against a fixed set of columns.
    permitted_uses    VARCHAR(512) NOT NULL,

    max_quote_chars   INTEGER      NOT NULL DEFAULT 0,

    -- NULL means never restricted. An embargo in the past means "was restricted and is
    -- not any more", which the audit trail needs to distinguish.
    embargo_until     TIMESTAMPTZ,

    redistribution    VARCHAR(32)  NOT NULL,
    retention_policy  VARCHAR(128),

    CONSTRAINT source_contract_redistribution_check
        CHECK (redistribution IN ('PUBLIC', 'LICENSED_BROAD',
                                  'LICENSED_CLIENTS_ONLY', 'TENANT_PRIVATE'))
);

CREATE TABLE catalog_document (
    document_id       VARCHAR(128) PRIMARY KEY,
    source_id         VARCHAR(128) NOT NULL,
    doc_type          VARCHAR(48)  NOT NULL,

    -- Immutable once written. Every temporal ranking decision and every currency
    -- judgement rests on it.
    published_at      TIMESTAMPTZ  NOT NULL,

    -- The bytes live in the landing zone; this addresses them. Unique because content
    -- addressing is what makes registration idempotent -- acquisition is retried,
    -- replayed and backfilled, and without this a replayed feed produces duplicate
    -- entries that an agent counts as independent corroboration.
    content_hash      VARCHAR(128) NOT NULL,
    storage_key       VARCHAR(512) NOT NULL,

    status            VARCHAR(16)  NOT NULL,
    superseded_by     VARCHAR(128),
    retraction_reason VARCHAR(512),

    CONSTRAINT catalog_document_hash_unique UNIQUE (content_hash),
    CONSTRAINT catalog_document_status_check
        CHECK (status IN ('PUBLISHED', 'SUPERSEDED', 'RETRACTED')),

    -- "Out of date" without a pointer to the replacement is not actionable by an agent
    -- and not explicable to a user.
    CONSTRAINT catalog_document_superseded_has_successor
        CHECK (status <> 'SUPERSEDED' OR superseded_by IS NOT NULL),
    CONSTRAINT catalog_document_no_self_supersession
        CHECK (superseded_by IS NULL OR superseded_by <> document_id),
    CONSTRAINT catalog_document_retraction_has_reason
        CHECK (status <> 'RETRACTED' OR retraction_reason IS NOT NULL)
);

-- The takedown path: "everything from this source" is how a licence termination or a
-- publisher withdrawal arrives, and it must not degrade as the corpus grows.
CREATE INDEX catalog_document_by_source ON catalog_document (source_id, status);
