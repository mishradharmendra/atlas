package com.atlas.tenancy.domain.model;

import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.Validity;
import java.util.Objects;

/**
 * What a tenant bought, over the window they bought it for.
 *
 * <p>A record with a validity rather than a mutable seat count. Overwriting the count is what
 * makes a past invoice unreconstructible, and the first billing dispute is always about a past
 * period.
 */
public record Subscription(
        String planId, int seats, long seatPriceMicros, Validity validity, boolean trial) {

    public Subscription {
        Objects.requireNonNull(validity, "subscription validity");
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("a subscription must name its plan");
        }
        if (seats < 1) {
            throw new IllegalArgumentException(
                    "a subscription with no seats is not a subscription; a customer who has "
                            + "stopped paying is a suspended tenant, which is a different fact");
        }
        if (seatPriceMicros < 0) {
            throw new IllegalArgumentException("seat price must not be negative");
        }
        if (trial && seatPriceMicros != 0) {
            throw new IllegalArgumentException(
                    "a trial with a seat price is a paid subscription. Conflating them makes "
                            + "every margin report treat trials as loss-making customers");
        }
    }

    public boolean coversAt(AsOf at) {
        return validity.contains(at);
    }

    public long revenueMicros() {
        return (long) seats * seatPriceMicros;
    }
}
