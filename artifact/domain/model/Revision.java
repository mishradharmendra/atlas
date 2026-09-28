package com.atlas.artifact.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry in a deliverable's append-only history.
 *
 * <p>A deliverable that can be edited in place cannot answer "what did we send them in March?",
 * and that is the first question asked when a client disputes a number. So nothing is mutated:
 * every change appends, and the artifact at any past instant is the fold of its revisions up to
 * that point.
 */
public record Revision(
        int sequence, RevisionKind kind, String cellRef, String detail, String by, Instant at) {

    public Revision {
        if (sequence < 0) {
            throw new IllegalArgumentException("revision sequence must not be negative");
        }
        Objects.requireNonNull(kind, "revision kind");
        Objects.requireNonNull(at, "revision instant");
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException(
                    "a revision must name who or what made it; an unattributed change to a "
                            + "client deliverable cannot be defended");
        }
    }
}
