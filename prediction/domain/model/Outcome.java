package com.atlas.prediction.domain.model;

/**
 * What the world did about a prediction.
 *
 * <h2>Why UNRESOLVABLE is not a kind of wrong</h2>
 *
 * <p>It is the distinction the whole scoreboard depends on. A prediction nobody can settle —
 * the company stopped disclosing the segment, the deal was never announced either way — says
 * nothing about whether the sources behind it were any good. Folding it into INCORRECT charges
 * them for the world's ambiguity, and it does so unevenly: the sources covering genuinely murky
 * subjects take the penalty, which is the opposite of the ranking anyone wants.
 *
 * <p>It is also the honest place for the pressure to go. A grader who must pick CORRECT or
 * INCORRECT for a claim that did not really resolve will pick one, and which one depends on what
 * they hoped. Given a third option, the same grader records the ambiguity, and a source with many
 * unresolvable predictions is then visible as exactly that — which is its own finding, and
 * usually means the criterion was written badly rather than the source being poor.
 */
public enum Outcome {

    /** The stated criterion was met. */
    CORRECT,

    /** The stated criterion was not met. */
    INCORRECT,

    /** The criterion could not be evaluated. Scored by nobody, counted separately. */
    UNRESOLVABLE;

    /** Whether this outcome can contribute to a score. */
    public boolean isDecidable() {
        return this != UNRESOLVABLE;
    }
}
