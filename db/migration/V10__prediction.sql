-- G6.4 prediction: the feedback loop that makes sources earn their keep.
--
-- The CHECK constraints below duplicate the aggregate deliberately. Predictions are exactly the
-- kind of table someone backfills with a script during a migration or a demo, and a bulk loader
-- does not go through the aggregate. A retrodiction inserted that way would score perfectly and
-- lift whichever source it named.

CREATE TABLE prediction (
    prediction_id        VARCHAR(128)     PRIMARY KEY,
    tenant_id            UUID             NOT NULL,

    claim                VARCHAR(2048)    NOT NULL,

    -- The falsifiability rule. A claim with no criterion is graded later by whoever reads it,
    -- against whatever they remember having expected.
    resolution_criterion VARCHAR(2048)    NOT NULL,

    confidence           DOUBLE PRECISION NOT NULL,
    made_at              TIMESTAMPTZ      NOT NULL,
    resolves_at          TIMESTAMPTZ      NOT NULL,

    -- The deliverable this prediction was published in, when there was one.
    artifact_id          VARCHAR(128),

    status               VARCHAR(16)      NOT NULL,
    outcome              VARCHAR(16),
    resolved_at          TIMESTAMPTZ,
    resolution_note      VARCHAR(2048),

    CONSTRAINT prediction_criterion_not_blank
        CHECK (length(btrim(resolution_criterion)) > 0),

    -- A claim about the past, written by an author who already knows the answer.
    CONSTRAINT prediction_resolves_in_the_future
        CHECK (resolves_at > made_at),

    -- Certainty cannot be moved by evidence, which is the only reason to write a number down.
    CONSTRAINT prediction_confidence_is_not_certainty
        CHECK (confidence > 0 AND confidence < 1),

    CONSTRAINT prediction_status_check
        CHECK (status IN ('OPEN', 'RESOLVED', 'OVERDUE')),

    CONSTRAINT prediction_outcome_check
        CHECK (outcome IS NULL OR outcome IN ('CORRECT', 'INCORRECT', 'UNRESOLVABLE')),

    CONSTRAINT prediction_resolved_is_graded
        CHECK (status <> 'RESOLVED' OR (outcome IS NOT NULL AND resolved_at IS NOT NULL)),

    -- Unresolvable is the outcome that costs nobody anything, so it is the one that has to
    -- justify itself.
    CONSTRAINT prediction_unresolvable_has_a_reason
        CHECK (outcome IS DISTINCT FROM 'UNRESOLVABLE'
               OR length(btrim(coalesce(resolution_note, ''))) > 0),

    -- Grading early means choosing when to measure, and the moment gets chosen when the answer
    -- is flattering.
    CONSTRAINT prediction_not_graded_early
        CHECK (resolved_at IS NULL OR resolved_at >= resolves_at)
);

-- Which sources a prediction rested on. A separate table because the outcome is attributed to
-- every one of them: any apportionment between two sources would be invented, and an invented
-- split is indistinguishable from a measured one once it reaches a dashboard.
CREATE TABLE prediction_source (
    prediction_id VARCHAR(128) NOT NULL REFERENCES prediction (prediction_id) ON DELETE CASCADE,
    source_id     VARCHAR(128) NOT NULL,

    PRIMARY KEY (prediction_id, source_id)
);

-- The seek behind every weight calculation.
CREATE INDEX prediction_source_by_source ON prediction_source (source_id);

-- The sweep that finds predictions nobody graded.
CREATE INDEX prediction_due ON prediction (resolves_at) WHERE status = 'OPEN';

CREATE INDEX prediction_by_tenant ON prediction (tenant_id, made_at DESC);

-- A prediction must name at least one source, which no CHECK can express because the sources
-- live in another table. Deferred to commit so the two inserts can happen in either order
-- within a transaction, and enforced at all because the aggregate's version of this rule is
-- bypassed by exactly the backfills this constraint exists to catch: a prediction attributed to
-- nobody is graded and the grade goes nowhere, which is silent rather than wrong.
CREATE FUNCTION prediction_has_a_source() RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM prediction_source WHERE prediction_id = NEW.prediction_id) THEN
        RAISE EXCEPTION 'prediction_names_a_source: prediction % rests on no source, so its '
                        'outcome could never be attributed', NEW.prediction_id
            -- check_violation, so callers see this as the constraint breach it is rather than
            -- as an unclassified database error they are tempted to retry.
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER prediction_names_a_source
    AFTER INSERT ON prediction
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION prediction_has_a_source();
