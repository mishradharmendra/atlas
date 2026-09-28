package com.atlas.shared.outcome;

import com.atlas.shared.provenance.Citation;
import java.util.List;

/**
 * Disagreement between sources, surfaced rather than silently resolved.
 *
 * <p>The tempting behaviour is to pick whichever source ranked highest and say nothing. That
 * produces a cleaner-looking answer and destroys trust the first time a professional notices their
 * own preferred source was contradicted without mention. Conflict is often the most valuable thing
 * the system found: two brokers disagreeing on a number is a signal, not noise to be averaged away.
 */
public record Conflict(String claim, List<Citation> positions, ConflictKind kind) {

    public Conflict {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("conflict must state the claim in dispute");
        }
        if (positions == null || positions.size() < 2) {
            throw new IllegalArgumentException(
                    "a conflict needs at least two cited positions; one position is just evidence");
        }
        if (kind == null) {
            throw new IllegalArgumentException("conflict must carry a kind");
        }
        positions = List.copyOf(positions);
    }

    /**
     * A supersession is resolvable without judgement: a restatement replaces the figure it
     * restates. Every other kind requires a human or an explicit analytical rule.
     */
    public boolean isMechanicallyResolvable() {
        return kind == ConflictKind.SUPERSESSION;
    }
}
