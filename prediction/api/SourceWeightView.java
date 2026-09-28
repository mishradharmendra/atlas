package com.atlas.prediction.api;

/**
 * A source's standing, as a transferable value.
 *
 * <p>{@code meanBrier} and {@code unresolvableRate} are boxed and are null below the sample floor.
 * Null rather than a sentinel like -1 or 0: a sentinel is a number, and a number gets sorted.
 * Callers that forget to check get a NullPointerException, which is the loud failure — the
 * alternative is a vendor ranking silently led by whichever source has the fewest data points.
 */
public record SourceWeightView(
        String sourceId,
        long scored,
        long unresolvable,
        Double meanBrier,
        Double unresolvableRate) {}
