package com.atlas.knowledge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA row for one citation supporting one edge. Normalised so the cascade can seek, not scan. */
@Entity
@Table(name = "knowledge_edge_citation")
class EdgeCitationRow {

    @Id
    @Column(name = "citation_id", nullable = false, length = 128)
    String citationId;

    @Column(name = "relationship_id", nullable = false, length = 128)
    String relationshipId;

    @Column(name = "span_id", nullable = false, length = 128)
    String spanId;

    @Column(name = "doc_id", nullable = false, length = 128)
    String docId;

    @Column(name = "page", nullable = false)
    int page;

    @Column(name = "char_start", nullable = false)
    int charStart;

    @Column(name = "char_end", nullable = false)
    int charEnd;

    @Column(name = "bbox_x0", nullable = false)
    float bboxX0;

    @Column(name = "bbox_y0", nullable = false)
    float bboxY0;

    @Column(name = "bbox_x1", nullable = false)
    float bboxX1;

    @Column(name = "bbox_y1", nullable = false)
    float bboxY1;

    @Column(name = "quoted_text", nullable = false, length = 2048)
    String quotedText;

    @Column(name = "authority", nullable = false, length = 16)
    String authority;

    @Column(name = "published_at", nullable = false)
    Instant publishedAt;

    @Column(name = "source_name", nullable = false, length = 256)
    String sourceName;

    protected EdgeCitationRow() {
        // required by JPA
    }
}
