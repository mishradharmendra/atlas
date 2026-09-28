package com.atlas.evaluation.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * JPA row for a rubric version.
 *
 * <p>Criteria are stored as a JSON document in one column rather than a child table. They are only
 * ever read as a whole — a rubric is graded against in its entirety — and once frozen they never
 * change, so the usual reasons for normalising a collection do not apply. What does apply is that
 * a frozen rubric must round-trip byte-identically, and a single opaque column makes that provable
 * rather than dependent on child-row ordering.
 */
@Entity
@Table(name = "eval_rubric")
@IdClass(RubricRow.Key.class)
class RubricRow {

    @Id
    @Column(name = "rubric_id", nullable = false, length = 128)
    String rubricId;

    @Id
    @Column(name = "version", nullable = false)
    int version;

    @Column(name = "question", nullable = false, length = 2048)
    String question;

    @Column(name = "as_of", nullable = false)
    Instant asOf;

    @Column(name = "status", nullable = false, length = 16)
    String status;

    @Column(name = "frozen_at")
    Instant frozenAt;

    @Column(name = "criteria", nullable = false, length = 65536)
    String criteria;

    protected RubricRow() {
        // required by JPA
    }

    record Key(String rubricId, int version) implements Serializable {
        Key() {
            this(null, 0);
        }
    }
}
