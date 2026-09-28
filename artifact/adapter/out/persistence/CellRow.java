package com.atlas.artifact.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA row for one bound value. */
@Entity
@Table(name = "artifact_cell")
@IdClass(CellRow.Key.class)
class CellRow {

    static class Key implements Serializable {
        String artifactId;
        String cellRef;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(artifactId, key.artifactId)
                    && Objects.equals(cellRef, key.cellRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactId, cellRef);
        }
    }

    @Id
    @Column(name = "artifact_id", nullable = false, length = 128)
    String artifactId;

    @Id
    @Column(name = "cell_ref", nullable = false, length = 128)
    String cellRef;

    @Column(name = "machine_value", length = 2048)
    String machineValue;

    @Column(name = "span_id", length = 128)
    String spanId;

    @Column(name = "doc_id", length = 128)
    String docId;

    @Column(name = "page")
    Integer page;

    @Column(name = "char_start")
    Integer charStart;

    @Column(name = "char_end")
    Integer charEnd;

    @Column(name = "quoted_text", length = 2048)
    String quotedText;

    @Column(name = "authority", length = 16)
    String authority;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "source_name", length = 256)
    String sourceName;

    @Column(name = "derived_from", length = 1024)
    String derivedFrom;

    @Column(name = "override_value", length = 2048)
    String overrideValue;

    @Column(name = "overridden_by", length = 128)
    String overriddenBy;

    @Column(name = "overridden_at")
    Instant overriddenAt;

    @Column(name = "override_rationale", length = 1024)
    String overrideRationale;

    @Column(name = "machine_value_when_overridden", length = 2048)
    String machineValueWhenOverridden;

    protected CellRow() {
        // required by JPA
    }
}
