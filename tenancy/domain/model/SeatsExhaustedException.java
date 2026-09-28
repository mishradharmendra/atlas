package com.atlas.tenancy.domain.model;

/**
 * Raised when a seat assignment would exceed what the tenant bought.
 *
 * <p>Carries the numbers because the next action depends on them: one over is a conversation
 * about an upgrade, twenty over is a provisioning bug. A bare refusal reads as the former even
 * when it is the latter.
 */
public class SeatsExhaustedException extends IllegalStateException {

    private final transient int subscribed;
    private final transient long requested;

    public SeatsExhaustedException(String tenantId, int subscribed, long requested) {
        super("tenant %s has %d seat(s); assigning this one would make %d"
                .formatted(tenantId, subscribed, requested));
        this.subscribed = subscribed;
        this.requested = requested;
    }

    public int subscribed() {
        return subscribed;
    }

    public long requested() {
        return requested;
    }
}
