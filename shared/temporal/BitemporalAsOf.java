package com.atlas.shared.temporal;

import java.time.Instant;
import java.util.Objects;

/**
 * The two clocks a graph traversal needs: what was true, and what we knew.
 *
 * <p>{@link #validAt} is world time — when the fact held. {@link #knownAt} is system time — when
 * the platform believed it. Both are mandatory and neither defaults, because the interesting
 * questions need them to differ.
 *
 * <h2>Why one clock is not enough</h2>
 *
 * <p>World time alone answers "who supplied them in Q2?" but cannot answer it <em>reproducibly</em>.
 * An extraction corrected last week silently rewrites every historical answer, so a run cannot be
 * replayed, a regression cannot be bisected, and an analyst who published a number in March cannot
 * show why the system now says something else.
 *
 * <p>System time alone answers "what did we believe in March?" but conflates the correction of a
 * mistake with a genuine change in the world — which are the two cases a research platform most
 * needs to keep apart.
 *
 * <p>With both: "as the world stood in Q2, according to what we knew in March" is a single,
 * answerable, reproducible query. That pair is the unit of replay.
 */
public record BitemporalAsOf(AsOf validAt, AsOf knownAt) {

    public BitemporalAsOf {
        Objects.requireNonNull(validAt, "valid-at (world time)");
        Objects.requireNonNull(knownAt, "known-at (system time)");
    }

    public static BitemporalAsOf of(Instant validAt, Instant knownAt) {
        return new BitemporalAsOf(AsOf.at(validAt), AsOf.at(knownAt));
    }

    /**
     * The world at {@code validAt} as currently believed.
     *
     * <p>Named for what it does. There is no {@code now()} and no no-argument form: the caller has
     * to write down which instant they are treating as the present, so the query that produced an
     * answer can be reconstructed from the answer.
     */
    public static BitemporalAsOf asBelievedAt(Instant validAt, Instant believedAt) {
        return of(validAt, believedAt);
    }

    @Override
    public String toString() {
        return "valid@%s known@%s".formatted(validAt, knownAt);
    }
}
