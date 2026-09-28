package com.atlas.artifact.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA row for one entry of the append-only history. */
@Entity
@Table(name = "artifact_revision")
@IdClass(RevisionRow.Key.class)
class RevisionRow {

    static class Key implements Serializable {
        String artifactId;
        Integer sequence;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(artifactId, key.artifactId)
                    && Objects.equals(sequence, key.sequence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactId, sequence);
        }
    }

    @Id
    @Column(name = "artifact_id", nullable = false, length = 128)
    String artifactId;

    @Id
    @Column(name = "sequence", nullable = false)
    Integer sequence;

    @Column(name = "kind", nullable = false, length = 24)
    String kind;

    @Column(name = "cell_ref", length = 128)
    String cellRef;

    @Column(name = "detail", length = 2048)
    String detail;

    @Column(name = "made_by", nullable = false, length = 128)
    String madeBy;

    @Column(name = "made_at", nullable = false)
    Instant madeAt;

    protected RevisionRow() {
        // required by JPA
    }
}
