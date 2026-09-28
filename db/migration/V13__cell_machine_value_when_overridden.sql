-- Divergence needs the reading the analyst was looking at, not just the current one.
--
-- Without this column `divergesFromSource()` compared the correction against the current machine
-- value, which differs the instant a correction is made. Every overridden cell reported itself as
-- diverging, so the state meant "corrected" and a genuine restatement looked like everything else.
ALTER TABLE artifact_cell
    ADD COLUMN machine_value_when_overridden VARCHAR(2048);

-- Backfill: existing corrections were recorded against the machine value still stored beside
-- them, so that is the reading they were made against. Leaving these null would report every
-- historical correction as never having diverged, which is the opposite error.
UPDATE artifact_cell
SET machine_value_when_overridden = machine_value
WHERE override_value IS NOT NULL;

-- A correction always knows what it corrected. Enforced so a backfill or an import cannot leave
-- the column behind and quietly disable the signal.
ALTER TABLE artifact_cell
    ADD CONSTRAINT artifact_cell_override_knows_what_it_corrected
        CHECK (override_value IS NULL OR machine_value_when_overridden IS NOT NULL);
