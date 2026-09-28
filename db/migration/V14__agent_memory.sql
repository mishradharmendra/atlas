-- Agent memory: what a run may carry into the next one.
--
-- Separate from research_trace_entry, which records what a run *did*. Memory records what it
-- concluded and intends to repeat, which is a claim rather than a log line and therefore needs
-- a source, an entitlement and an expiry.
--
-- Source documents and entitlement tags are held in child tables rather than as arrays, because
-- the retraction cascade asks "which entries cite this document?" with a publisher's lawyer
-- waiting, and that question over an array column is a scan of every row.

CREATE TABLE agent_memory (
    entry_id         VARCHAR(128) PRIMARY KEY,
    tenant_id        VARCHAR(128) NOT NULL,
    kind             VARCHAR(32)  NOT NULL,
    text             TEXT         NOT NULL,
    written_by_run   VARCHAR(128) NOT NULL,
    written_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ,
    withdrawn        BOOLEAN      NOT NULL DEFAULT FALSE,
    withdrawn_reason VARCHAR(1024),

    CONSTRAINT agent_memory_kind_check
        CHECK (kind IN ('ESTABLISHED_FACT', 'PREFERENCE', 'PRIOR_CONCLUSION', 'FAILURE')),

    -- An entry cannot expire before it was written. Enforced here as well as in the aggregate
    -- because an import that bypassed the aggregate would otherwise create an entry that is
    -- never readable and never obviously wrong.
    CONSTRAINT agent_memory_expiry_after_write
        CHECK (expires_at IS NULL OR expires_at > written_at),

    CONSTRAINT agent_memory_withdrawn_has_reason
        CHECK (withdrawn = FALSE OR withdrawn_reason IS NOT NULL)
);

-- The recall path: one tenant, live now, newest first.
CREATE INDEX agent_memory_live ON agent_memory (tenant_id, withdrawn, expires_at, written_at DESC);

CREATE TABLE agent_memory_source (
    entry_id VARCHAR(128) NOT NULL REFERENCES agent_memory (entry_id) ON DELETE CASCADE,
    doc_id   VARCHAR(128) NOT NULL,
    PRIMARY KEY (entry_id, doc_id)
);

-- The takedown path. Without this index, "forget everything from this document" is a full scan.
CREATE INDEX agent_memory_source_doc ON agent_memory_source (doc_id);

CREATE TABLE agent_memory_tag (
    entry_id VARCHAR(128) NOT NULL REFERENCES agent_memory (entry_id) ON DELETE CASCADE,
    tag      VARCHAR(128) NOT NULL,
    PRIMARY KEY (entry_id, tag)
);
