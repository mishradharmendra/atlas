package com.atlas.tenancy.api;

/**
 * What a tenant was paying at a point in time.
 *
 * <p>{@code trial} travels with the numbers rather than being inferred from a zero price. A
 * caller that inferred it would treat a free pilot for a paying customer as a trial, and the two
 * belong in different columns of a margin report.
 */
public record SubscriptionView(
        String tenantId, String planId, int seats, long seatPriceMicros, boolean trial) {

    public long revenueMicros() {
        return (long) seats * seatPriceMicros;
    }
}
