package com.atlas.knowledge.api;

import com.atlas.shared.outcome.Abstention;
import java.util.List;

/**
 * The outcome of trying to turn a name into an entity: resolved, or an explicit refusal.
 *
 * <p>A sealed pair rather than a nullable id. The nullable version compiles everywhere it is
 * misused; this one does not let a caller reach the id without acknowledging that the other case
 * exists, and carries <em>why</em> into the answer so a user can be told "that name is ambiguous
 * between these two" rather than being given one of them.
 *
 * <p>There is deliberately no {@code orElse} and no {@code getOrNull}. Both exist to let a caller
 * skip the second case, which is the only case that matters.
 */
public sealed interface Resolution {

    record Resolved(String entityId, String canonicalName, double confidence) implements Resolution {
        public Resolved {
            if (confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("confidence must be in [0,1], was " + confidence);
            }
        }
    }

    /**
     * No entity was asserted, and here is why.
     *
     * <p>{@code candidates} is populated for the ambiguous case. An analyst told "ambiguous" is
     * stuck; an analyst told "ambiguous between Apple Inc and Apple Corps" has been handed the
     * answer in the next click.
     */
    record Abstained(Abstention abstention, List<String> candidates) implements Resolution {
        public Abstained {
            candidates = List.copyOf(candidates);
        }
    }
}
