package com.atlas.artifact.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA row for a deliverable's identity and lifecycle. */
@Entity
@Table(name = "artifact")
class ArtifactRow {

    @Id
    @Column(name = "artifact_id", nullable = false, length = 128)
    String artifactId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "run_id", nullable = false, length = 128)
    String runId;

    @Column(name = "title", nullable = false, length = 512)
    String title;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "status", nullable = false, length = 16)
    String status;

    @Column(name = "issued_at")
    Instant issuedAt;

    @Column(name = "flag_reason", length = 1024)
    String flagReason;

    protected ArtifactRow() {
        // required by JPA
    }
}
