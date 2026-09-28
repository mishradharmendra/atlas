package com.atlas.metering.domain.model;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What a tenant has cost over a period, against what they pay.
 *
 * <h2>The number that matters is per seat, not per run</h2>
 *
 * <p>Cost per run is an engineering metric and it is reassuring: runs are cheap. Seat revenue is
 * flat while usage is not, so a tenant with ten seats and the usage of twelve is losing money on
 * a product that looks healthy by every per-run measure. {@link #marginRatio()} is the number a
 * commercial conversation is held about.
 *
 * <p>It is a ratio rather than a boolean because "unprofitable" is not the interesting threshold.
 * A tenant at 0.8 is fine and worth watching; one at 0.3 is a pricing problem; one at 1.4 is an
 * incident. A boolean collapses those into the same alert.
 */
public record TenantUsage(
        String tenantId,
        String period,
        int seats,
        long seatPriceMicros,
        long runs,
        long tokens,
        long costMicros) {

    /** Below this many runs a cost-per-run figure is noise, and a ratio built on it is worse. */
    public static final long MIN_RUNS = 10;

    public TenantUsage {
        Objects.requireNonNull(tenantId, "tenant id");
        Objects.requireNonNull(period, "period");
        if (seats < 0 || seatPriceMicros < 0) {
            throw new IllegalArgumentException("seats and seat price must not be negative");
        }
    }

    /** The same usage, priced against what the tenant was actually paying then. */
    public TenantUsage pricedAt(int seats, long seatPriceMicros) {
        return new TenantUsage(tenantId, period, seats, seatPriceMicros, runs, tokens, costMicros);
    }

    public long revenueMicros() {
        return (long) seats * seatPriceMicros;
    }

    /**
     * Cost as a fraction of subscription revenue. Above 1.0 the tenant costs more than it pays.
     *
     * <p>Absent when there is no subscription to compare against — a trial, or an internal
     * tenant. Reporting those as infinitely unprofitable would bury the real ones.
     */
    public OptionalDouble marginRatio() {
        long revenue = revenueMicros();
        if (revenue <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) costMicros / revenue);
    }

    /** Withheld below {@link #MIN_RUNS}: an average over three runs is not an average. */
    public OptionalDouble costPerRunMicros() {
        if (runs < MIN_RUNS) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) costMicros / runs);
    }

    /**
     * Whether this tenant is costing more than it pays.
     *
     * <p>Only answerable with a subscription to compare against; {@code false} here means "not
     * known to be", which is why the ratio is what gets reported rather than this.
     */
    public boolean isUnprofitable() {
        return marginRatio().orElse(0d) > 1.0d;
    }
}
