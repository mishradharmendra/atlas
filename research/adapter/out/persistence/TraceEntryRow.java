package com.atlas.research.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA row for one trace entry. */
@Entity
@Table(name = "research_trace_entry")
@IdClass(TraceEntryRow.Key.class)
class TraceEntryRow {

    @Id
    @Column(name = "run_id", nullable = false, length = 128)
    String runId;

    @Id
    @Column(name = "sequence", nullable = false)
    Integer sequence;

    @Column(name = "step_id", nullable = false, length = 128)
    String stepId;

    @Column(name = "attempt", nullable = false)
    int attempt;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "took_millis", nullable = false)
    long tookMillis;

    @Column(name = "action", nullable = false, length = 4096)
    String action;

    @Column(name = "observation", nullable = false, length = 8192)
    String observation;

    @Column(name = "outcome", nullable = false, length = 16)
    String outcome;

    @Column(name = "abstention_reason", length = 32)
    String abstentionReason;

    @Column(name = "abstention_detail", length = 1024)
    String abstentionDetail;

    @Column(name = "abstention_coverage")
    Float abstentionCoverage;

    @Column(name = "tokens", nullable = false)
    long tokens;

    @Column(name = "cost_micros", nullable = false)
    long costMicros;

    protected TraceEntryRow() {
        // required by JPA
    }

    static class Key implements Serializable {
        String runId;
        Integer sequence;

        Key() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(runId, key.runId)
                    && Objects.equals(sequence, key.sequence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, sequence);
        }
    }
}
