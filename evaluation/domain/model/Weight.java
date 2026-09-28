package com.atlas.evaluation.domain.model;

/**
 * How much a criterion counts, on a scale of −5 to +5 that excludes zero.
 *
 * <p>Zero is excluded because a criterion worth nothing is not a criterion. It is a note somebody
 * was unwilling to delete, and it survives review precisely because it changes no score — then
 * accumulates, until the rubric reads as thorough and grades on a third of its text.
 *
 * <p>Negative weights are not penalties for doing badly on a positive axis. They name
 * <em>characteristic failures</em>: the specific wrong thing this kind of question provokes. "Cites
 * the superseded filing" is a different assertion from "scores low on temporal", and only the first
 * one tells you what to fix.
 */
public record Weight(int value) implements Comparable<Weight> {

    public static final int MIN = -5;
    public static final int MAX = 5;

    /**
     * At or above this, a criterion is mandatory: failing it fails the answer outright, whatever
     * the total. Some requirements are not tradeable against volume of other virtues.
     */
    public static final int MANDATORY_THRESHOLD = 5;

    public Weight {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                    "weight must be between %d and %d, was %d".formatted(MIN, MAX, value));
        }
        if (value == 0) {
            throw new IllegalArgumentException(
                    "weight must not be zero: a criterion that changes no score is a comment, and "
                            + "it will accumulate until the rubric reads as thorough and grades on "
                            + "a third of its text");
        }
    }

    public static Weight of(int value) {
        return new Weight(value);
    }

    /** Failing this criterion fails the answer regardless of the total. */
    public boolean isMandatory() {
        return value >= MANDATORY_THRESHOLD;
    }

    /** Names a characteristic failure rather than a virtue. */
    public boolean isCharacteristicFailure() {
        return value < 0;
    }

    public int positiveContribution() {
        return Math.max(0, value);
    }

    @Override
    public int compareTo(Weight other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return (value > 0 ? "+" : "") + value;
    }
}
