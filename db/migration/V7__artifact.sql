-- Deliverables: the documents an analyst actually sends.
--
-- Three shapes here, and the split matters. The artifact row is identity and lifecycle;
-- cells are the bound values; revisions are the append-only history. Folding cells into a
-- JSON blob on the artifact would make "which deliverables cite this document?" a scan of
-- every row, and that question is asked by the retraction cascade with a publisher's
-- lawyer waiting.

CREATE TABLE artifact (
    artifact_id  VARCHAR(128) PRIMARY KEY,
    tenant_id    UUID         NOT NULL,

    -- The run behind the document. Without it the trace, the budget and the skill version
    -- that produced a client deliverable are all unrecoverable.
    run_id       VARCHAR(128) NOT NULL,

    title        VARCHAR(512) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    issued_at    TIMESTAMPTZ,
    flag_reason  VARCHAR(1024),

    CONSTRAINT artifact_status_check
        CHECK (status IN ('DRAFT', 'ISSUED', 'FLAGGED', 'WITHDRAWN')),

    -- An issued artifact knows when it was sent. "We sent it, we do not know when" is not
    -- an answer to a client dispute.
    CONSTRAINT artifact_issued_has_timestamp
        CHECK (status = 'DRAFT' OR issued_at IS NOT NULL)
);

CREATE TABLE artifact_cell (
    artifact_id        VARCHAR(128) NOT NULL REFERENCES artifact (artifact_id) ON DELETE CASCADE,
    cell_ref           VARCHAR(128) NOT NULL,

    machine_value      VARCHAR(2048),

    -- Provenance, flattened. doc_id is indexed below because the cascade seeks on it.
    span_id            VARCHAR(128),
    doc_id             VARCHAR(128),
    page               INTEGER,
    char_start         INTEGER,
    char_end           INTEGER,
    quoted_text        VARCHAR(2048),
    authority          VARCHAR(16),
    published_at       TIMESTAMPTZ,
    source_name        VARCHAR(256),

    -- For computed cells, whose defensibility comes from the cells they were computed from.
    derived_from       VARCHAR(1024),

    -- The analyst's correction, stored ALONGSIDE machine_value and never in place of it.
    -- Replacing it would destroy the one comparison worth having: "the filing now reads
    -- 412, you told me 388".
    override_value     VARCHAR(2048),
    overridden_by      VARCHAR(128),
    overridden_at      TIMESTAMPTZ,
    override_rationale VARCHAR(1024),

    PRIMARY KEY (artifact_id, cell_ref),

    -- A naked number in a client deliverable cannot be defended.
    CONSTRAINT artifact_cell_has_provenance
        CHECK (doc_id IS NOT NULL OR derived_from IS NOT NULL),

    -- An anonymous, unexplained correction cannot be audited, and six months later the
    -- reason is the only thing separating a correction from a mistake.
    CONSTRAINT artifact_cell_override_is_attributed
        CHECK (override_value IS NULL
               OR (overridden_by IS NOT NULL AND override_rationale IS NOT NULL))
);

-- The cascade's access path: "every deliverable citing this document".
CREATE INDEX artifact_cell_by_doc ON artifact_cell (doc_id) WHERE doc_id IS NOT NULL;

CREATE TABLE artifact_revision (
    artifact_id VARCHAR(128) NOT NULL REFERENCES artifact (artifact_id) ON DELETE CASCADE,
    sequence    INTEGER      NOT NULL,
    kind        VARCHAR(24)  NOT NULL,
    cell_ref    VARCHAR(128),
    detail      VARCHAR(2048),
    made_by     VARCHAR(128) NOT NULL,
    made_at     TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (artifact_id, sequence),

    CONSTRAINT artifact_revision_kind_check
        CHECK (kind IN ('CELL_BOUND', 'CELL_OVERRIDDEN', 'CELL_REFRESHED',
                        'ISSUED', 'SOURCE_RETRACTED', 'WITHDRAWN'))
);
