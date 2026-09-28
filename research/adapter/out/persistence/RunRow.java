package com.atlas.research.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for a run. The trace lives in its own table, keyed by (run_id, sequence). */
@Entity
@Table(name = "research_run")
class RunRow {

    @Id
    @Column(name = "run_id", nullable = false, length = 128)
    String runId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "principal_id", nullable = false)
    UUID principalId;

    @Column(name = "question", nullable = false, length = 2048)
    String question;

    @Column(name = "skill_versioned_id", nullable = false, length = 160)
    String skillVersionedId;

    @Column(name = "mandatory_steps", nullable = false, length = 1024)
    String mandatorySteps;

    @Column(name = "config_fingerprint", nullable = false, length = 1024)
    String configFingerprint;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "status", nullable = false, length = 16)
    String status;

    @Column(name = "status_reason", length = 1024)
    String statusReason;

    @Column(name = "artefact_id", length = 128)
    String artefactId;

    @Column(name = "lease_held_by", length = 128)
    String leaseHeldBy;

    @Column(name = "lease_expires_at")
    java.time.Instant leaseExpiresAt;

    @Column(name = "max_tokens", nullable = false)
    long maxTokens;

    @Column(name = "max_wall_millis", nullable = false)
    long maxWallMillis;

    @Column(name = "max_spend_micros", nullable = false)
    long maxSpendMicros;

    protected RunRow() {
        // required by JPA
    }
}
