-- Lease-based durable execution.
--
-- The gap this closes: an executor dies mid-run and the run sits RUNNING for ever. No
-- error is raised anywhere, the user waits, and the budget stays reserved against work
-- that stopped hours ago.
--
-- A lease makes that failure detectable with the database that already holds the saga,
-- rather than by adding a workflow engine with its own server, its own worker deployment
-- and a second source of truth about run state.

ALTER TABLE research_run
    -- Which executor is driving this run. NULL means nobody has claimed it yet, which is
    -- different from a lease that has expired: the first has not started, the second has
    -- stopped, and only the second is an orphan.
    ADD COLUMN lease_held_by    VARCHAR(128),
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

ALTER TABLE research_run
    ADD CONSTRAINT research_run_lease_is_complete
        CHECK ((lease_held_by IS NULL) = (lease_expires_at IS NULL));

-- The reaper's access path: RUNNING rows whose lease has lapsed, oldest first. Partial,
-- because orphan detection only ever asks about running rows and the index would otherwise
-- carry every completed run in the table for no benefit.
CREATE INDEX research_run_orphaned
    ON research_run (lease_expires_at)
    WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL;
