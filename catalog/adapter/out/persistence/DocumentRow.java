package com.atlas.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA row for a catalogued document. Separate from the aggregate, which has invariants. */
@Entity
@Table(name = "catalog_document")
class DocumentRow {

    @Id
    @Column(name = "document_id", nullable = false, length = 128)
    String documentId;

    @Column(name = "source_id", nullable = false, length = 128)
    String sourceId;

    @Column(name = "doc_type", nullable = false, length = 48)
    String docType;

    @Column(name = "published_at", nullable = false)
    Instant publishedAt;

    @Column(name = "content_hash", nullable = false, length = 128)
    String contentHash;

    @Column(name = "storage_key", nullable = false, length = 512)
    String storageKey;

    @Column(name = "status", nullable = false, length = 16)
    String status;

    @Column(name = "superseded_by", length = 128)
    String supersededBy;

    @Column(name = "retraction_reason", length = 512)
    String retractionReason;

    protected DocumentRow() {
        // required by JPA
    }
}
