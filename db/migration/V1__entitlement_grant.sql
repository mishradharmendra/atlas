-- Durable authorisation records.
--
-- Grants live for months and change through contract negotiation; snapshots are computed
-- from them and live for minutes. Keeping the history here is what makes a past run
-- explicable: "why did this see that document?" is answered by replaying the grants in
-- force at the run's as-of, rather than by inspecting state that has since moved on.

CREATE TABLE entitlement_grant (
    grant_id        UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL,

    -- NULL means the grant applies to every principal in the tenant. Firm-wide research
    -- licences are held this way; deal-team barriers are held per principal.
    principal_id    UUID,

    dimension       VARCHAR(32)  NOT NULL,
    grant_value     VARCHAR(256) NOT NULL,
    effect          VARCHAR(8)   NOT NULL,

    -- NULL bounds mean unbounded. A future effective_from is how embargoed content is
    -- expressed: granted, but not yet in force at an earlier as-of.
    effective_from  TIMESTAMPTZ,
    effective_to    TIMESTAMPTZ,

    contract_id     VARCHAR(128),

    CONSTRAINT entitlement_grant_effect_check CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT entitlement_grant_window_check
        CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_to >= effective_from)
);

-- Issuance runs on every query, so the lookup must not degrade with contract history.
CREATE INDEX entitlement_grant_lookup
    ON entitlement_grant (tenant_id, principal_id, effective_from, effective_to);
