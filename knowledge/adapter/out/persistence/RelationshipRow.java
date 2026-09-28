package com.atlas.knowledge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA row for a bitemporal edge.
 *
 * <p>The verifying extractors are four columns rather than one JSON blob so the database can
 * enforce that the two families differ. A constraint that can only be expressed in application
 * code is a constraint that a backfill script does not have.
 */
@Entity
@Table(name = "knowledge_relationship")
class RelationshipRow {

    @Id
    @Column(name = "relationship_id", nullable = false, length = 128)
    String relationshipId;

    @Column(name = "subject_id", nullable = false, length = 128)
    String subjectId;

    @Column(name = "relationship_type", nullable = false, length = 32)
    String relationshipType;

    @Column(name = "object_id", nullable = false, length = 128)
    String objectId;

    @Column(name = "valid_from", nullable = false)
    Instant validFrom;

    @Column(name = "valid_to")
    Instant validTo;

    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    @Column(name = "superseded_at")
    Instant supersededAt;

    @Column(name = "status", nullable = false, length = 16)
    String status;

    @Column(name = "status_reason", length = 1024)
    String statusReason;

    @Column(name = "extractor_model", nullable = false, length = 128)
    String extractorModel;

    @Column(name = "extractor_family", nullable = false, length = 64)
    String extractorFamily;

    @Column(name = "verified_first_model", length = 128)
    String verifiedFirstModel;

    @Column(name = "verified_first_family", length = 64)
    String verifiedFirstFamily;

    @Column(name = "verified_second_model", length = 128)
    String verifiedSecondModel;

    @Column(name = "verified_second_family", length = 64)
    String verifiedSecondFamily;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "verified_statement", length = 2048)
    String verifiedStatement;

    protected RelationshipRow() {
        // required by JPA
    }
}
