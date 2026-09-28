-- The knowledge graph: canonical entities, the names they went by, and bitemporal edges.
--
-- Three constraints here are not conveniences. They are the same invariants the aggregates
-- enforce, restated where a backfill script, a hand-written correction or a future
-- "quick fix" cannot route around them:
--
--   1. an ACTIVE edge must carry two verifying extractors,
--   2. those two must be of different model families,
--   3. one entity cannot hold the same name over two overlapping windows.
--
-- The application enforces all three. So does this. An invariant that lives in only one of
-- the two places is an invariant that holds until the first direct UPDATE.

-- Needed for the exclusion constraint below: GiST has no built-in equality operator class
-- for varchar, and the alias rule is "same entity AND same name AND overlapping range".
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE knowledge_entity (
    entity_id      VARCHAR(128) PRIMARY KEY,
    kind           VARCHAR(32)  NOT NULL,

    -- Immutable. A rename is a new alias row; editing this would destroy the evidence that
    -- the old name ever applied, which is the only thing a historical corpus needs it for.
    canonical_name VARCHAR(512) NOT NULL,

    CONSTRAINT knowledge_entity_kind_check
        CHECK (kind IN ('COMPANY', 'PERSON', 'PRODUCT', 'SECTOR', 'PLACE', 'CLASSIFICATION'))
);

CREATE TABLE knowledge_alias (
    alias_id           VARCHAR(128) PRIMARY KEY,
    entity_id          VARCHAR(128) NOT NULL REFERENCES knowledge_entity (entity_id),

    -- Both the surface form and the matching form. The surface form is what gets shown to a
    -- user; storing only the normalised one would mean the platform could never quote the
    -- name as the filing actually wrote it.
    alias_value        VARCHAR(512) NOT NULL,
    normalised_value   VARCHAR(512) NOT NULL,

    valid_from         TIMESTAMPTZ  NOT NULL,

    -- NULL means "still in use as far as we know", which is not the same as "forever".
    valid_to           TIMESTAMPTZ,

    asserted_by_source VARCHAR(128) NOT NULL,

    CONSTRAINT knowledge_alias_window_check
        CHECK (valid_to IS NULL OR valid_to > valid_from),

    -- The overlap rule. Two records of the same name for the same entity covering overlapping
    -- periods are not a conflict to resolve, they are corrupt data: they double-count in any
    -- scoring that weighs how many sources use a name, and they make resolution depend on
    -- which row the index happened to return first.
    --
    -- Half-open '[)' so that an alias dropped and later readopted is legal, and so that a
    -- handover on the same day is not reported as a contradiction.
    CONSTRAINT knowledge_alias_no_overlapping_duplicates
        EXCLUDE USING gist (
            entity_id WITH =,
            normalised_value WITH =,
            tstzrange(valid_from, valid_to, '[)') WITH &&
        )
);

-- Resolution looks names up by normalised form at an instant, across all entities. Deliberately
-- not unique: "Apple" is a computer manufacturer and a record label, and both are correct. The
-- ambiguity is real and the resolver abstains on it.
CREATE INDEX knowledge_alias_by_name ON knowledge_alias (normalised_value, valid_from);

CREATE TABLE knowledge_relationship (
    relationship_id        VARCHAR(128) PRIMARY KEY,
    subject_id             VARCHAR(128) NOT NULL REFERENCES knowledge_entity (entity_id),
    relationship_type      VARCHAR(32)  NOT NULL,
    object_id              VARCHAR(128) NOT NULL REFERENCES knowledge_entity (entity_id),

    -- World time: when the relationship held.
    valid_from             TIMESTAMPTZ  NOT NULL,
    valid_to               TIMESTAMPTZ,

    -- System time: when the platform believed it. The pair is what makes an answer given in
    -- March reproducible in June after an extraction has been corrected.
    recorded_at            TIMESTAMPTZ  NOT NULL,
    superseded_at          TIMESTAMPTZ,

    status                 VARCHAR(16)  NOT NULL,
    status_reason          VARCHAR(1024),

    extractor_model        VARCHAR(128) NOT NULL,
    extractor_family       VARCHAR(64)  NOT NULL,

    verified_first_model   VARCHAR(128),
    verified_first_family  VARCHAR(64),
    verified_second_model  VARCHAR(128),
    verified_second_family VARCHAR(64),
    verified_at            TIMESTAMPTZ,
    verified_statement     VARCHAR(2048),

    CONSTRAINT knowledge_relationship_type_check
        CHECK (relationship_type IN ('SUPPLIES', 'CUSTOMER_OF', 'COMPETES_WITH', 'SUBSIDIARY_OF',
                                     'EXECUTIVE_OF', 'INVESTS_IN', 'PARTNERS_WITH', 'MEMBER_OF')),
    CONSTRAINT knowledge_relationship_status_check
        CHECK (status IN ('WITHHELD', 'ACTIVE', 'REJECTED', 'RETRACTED')),
    CONSTRAINT knowledge_relationship_window_check
        CHECK (valid_to IS NULL OR valid_to > valid_from),

    -- "A supplies A" is always an extraction error, and it poisons traversal with a
    -- length-one cycle.
    CONSTRAINT knowledge_relationship_no_self_loop
        CHECK (subject_id <> object_id),

    -- An edge is traversable only if two extractors signed for it.
    CONSTRAINT knowledge_relationship_active_is_verified
        CHECK (status <> 'ACTIVE'
               OR (verified_first_model IS NOT NULL AND verified_second_model IS NOT NULL)),

    -- And only if those two were of different families. Two checkpoints of one base model make
    -- the same mistakes on the same sentence, so their agreement measures determinism rather
    -- than correctness -- while producing a number that reads as corroboration.
    CONSTRAINT knowledge_relationship_verifiers_differ
        CHECK (verified_first_family IS NULL
               OR verified_second_family IS NULL
               OR verified_first_family <> verified_second_family)
);

-- Traversal: edges out of an entity that were ever verified. Keyed on verified_at rather than
-- status because a query about an earlier system time must still see edges retracted since.
CREATE INDEX knowledge_relationship_by_subject
    ON knowledge_relationship (subject_id, verified_at);

-- Symmetric predicates are stored once and walked from either end, so the reverse direction
-- needs its own index or every competitor query degrades into a sequential scan.
CREATE INDEX knowledge_relationship_by_object
    ON knowledge_relationship (object_id, verified_at);

-- Provenance, normalised rather than kept as a JSON blob on the edge.
--
-- The retraction cascade asks "every edge derived from this document" and it is a compliance
-- path, so it has to be an index seek. Inside a JSON column that question is a scan of the
-- whole table, which is fine in a demo and not fine at the point a publisher's lawyer is
-- waiting.
CREATE TABLE knowledge_edge_citation (
    citation_id     VARCHAR(128) PRIMARY KEY,
    relationship_id VARCHAR(128) NOT NULL
                    REFERENCES knowledge_relationship (relationship_id) ON DELETE CASCADE,

    span_id         VARCHAR(128) NOT NULL,
    doc_id          VARCHAR(128) NOT NULL,
    page            INTEGER      NOT NULL,
    char_start      INTEGER      NOT NULL,
    char_end        INTEGER      NOT NULL,
    bbox_x0         REAL         NOT NULL,
    bbox_y0         REAL         NOT NULL,
    bbox_x1         REAL         NOT NULL,
    bbox_y1         REAL         NOT NULL,

    -- Verbatim, never paraphrased. In this domain the signal is sometimes the exact word a
    -- CFO chose.
    quoted_text     VARCHAR(2048) NOT NULL,
    authority       VARCHAR(16)   NOT NULL,
    published_at    TIMESTAMPTZ   NOT NULL,
    source_name     VARCHAR(256)  NOT NULL,

    CONSTRAINT knowledge_edge_citation_authority_check
        CHECK (authority IN ('PRIMARY', 'SECONDARY', 'TERTIARY')),
    CONSTRAINT knowledge_edge_citation_range_check
        CHECK (char_end >= char_start)
);

-- The cascade's access path.
CREATE INDEX knowledge_edge_citation_by_doc ON knowledge_edge_citation (doc_id);
CREATE INDEX knowledge_edge_citation_by_edge ON knowledge_edge_citation (relationship_id);

-- The disagreement queue. A table rather than a log line because a disagreement is the most
-- informative event the extraction pipeline produces: it marks exactly the sentences that are
-- hard, and settling one yields a labelled example worth a hundred easy ones.
CREATE TABLE knowledge_review_item (
    review_item_id  VARCHAR(128) PRIMARY KEY,
    relationship_id VARCHAR(128) NOT NULL REFERENCES knowledge_relationship (relationship_id),

    proposed_model  VARCHAR(128) NOT NULL,
    proposed_family VARCHAR(64)  NOT NULL,
    dissent_model   VARCHAR(128) NOT NULL,
    dissent_family  VARCHAR(64)  NOT NULL,

    disagreement    VARCHAR(2048) NOT NULL,

    -- Carried on the item, not joined through the edge to the document to the source. Per-source
    -- disagreement rate is the metric that decides which feed to stop paying for, and a metric
    -- that needs a four-table join is a metric nobody computes.
    source_id       VARCHAR(128) NOT NULL,

    raised_at       TIMESTAMPTZ  NOT NULL,
    state           VARCHAR(16)  NOT NULL,
    claimed_by      VARCHAR(128),
    outcome         VARCHAR(16),
    resolved_by     VARCHAR(128),
    resolved_at     TIMESTAMPTZ,

    CONSTRAINT knowledge_review_state_check
        CHECK (state IN ('PENDING', 'CLAIMED', 'RESOLVED')),
    CONSTRAINT knowledge_review_outcome_check
        CHECK (outcome IS NULL OR outcome IN ('CONFIRMED', 'REFUTED', 'UNDECIDABLE')),

    -- A resolution with no name on it cannot be audited.
    CONSTRAINT knowledge_review_resolved_is_attributed
        CHECK (state <> 'RESOLVED' OR (outcome IS NOT NULL AND resolved_by IS NOT NULL)),

    -- Two reviewers settling the same item independently is how a queue silently produces
    -- contradictory labels.
    CONSTRAINT knowledge_review_claimed_has_claimant
        CHECK (state = 'PENDING' OR claimed_by IS NOT NULL),

    CONSTRAINT knowledge_review_extractors_differ
        CHECK (proposed_model <> dissent_model OR proposed_family <> dissent_family)
);

-- The queue is worked oldest-first; newest-first starves exactly the hard cases.
CREATE INDEX knowledge_review_pending ON knowledge_review_item (state, raised_at);
CREATE INDEX knowledge_review_by_source ON knowledge_review_item (source_id, outcome);
