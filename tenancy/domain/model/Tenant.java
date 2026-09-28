package com.atlas.tenancy.domain.model;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.Validity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A customer: their status, the subscriptions they have held, and who holds a seat.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Subscriptions never overlap.</b> Two subscriptions covering the same instant make
 *       "how many seats did they have?" ambiguous, and the answer is the denominator of every
 *       margin figure and every invoice.
 *   <li><b>Assigned seats never exceed subscribed seats.</b> The one that gets bypassed by "just
 *       add one more for the contractor" — and the overage is invisible, because nothing errors
 *       and the extra user works perfectly.
 *   <li><b>A principal holds at most one seat at a time.</b> Two concurrent assignments bill the
 *       same person twice and make per-seat usage figures wrong in a direction that flatters.
 *   <li><b>A closed tenant accepts nothing further.</b>
 * </ol>
 */
public class Tenant {

    private final TenantId id;
    private final String name;
    private final Instant createdAt;

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<SeatAssignment> seats = new ArrayList<>();

    private TenantStatus status = TenantStatus.TRIAL;
    private String statusReason;

    public Tenant(TenantId id, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "tenant id");
        this.createdAt = Objects.requireNonNull(createdAt, "created-at");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a tenant must have a name");
        }
        this.name = name;
    }

    // -- subscriptions ------------------------------------------------------

    /**
     * Records a subscription period.
     *
     * <p>Appends rather than replaces. A seat count changed in October must not retroactively
     * change the September invoice, and overwriting is exactly what would do that.
     */
    public void subscribe(Subscription subscription) {
        requireOpen("subscribe");
        for (Subscription existing : subscriptions) {
            if (existing.validity().overlaps(subscription.validity())) {
                throw new IllegalArgumentException(
                        ("tenant %s already has subscription '%s' over %s, which overlaps %s. "
                                        + "Two subscriptions covering one instant make the seat "
                                        + "count ambiguous, and that count is the denominator of "
                                        + "every invoice")
                                .formatted(
                                        id,
                                        existing.planId(),
                                        existing.validity(),
                                        subscription.validity()));
            }
        }
        subscriptions.add(subscription);
        if (!subscription.trial() && status == TenantStatus.TRIAL) {
            status = TenantStatus.ACTIVE;
        }
    }

    /** What they had bought at that instant. Empty between subscription periods. */
    public Optional<Subscription> subscriptionAt(AsOf at) {
        return subscriptions.stream().filter(subscription -> subscription.coversAt(at)).findFirst();
    }

    // -- seats --------------------------------------------------------------

    /**
     * Assigns a seat for a window.
     *
     * <p>Refuses when it would exceed the subscription, and refuses a principal who already holds
     * one over any overlapping window.
     */
    public void assignSeat(PrincipalId principal, Validity window) {
        requireOpen("assign a seat");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(window, "assignment window");

        Subscription subscription = subscriptionAt(AsOf.at(window.from()))
                .orElseThrow(() -> new IllegalStateException(
                        "tenant %s has no subscription covering %s".formatted(id, window.from())));

        for (SeatAssignment held : seats) {
            if (held.principal().equals(principal) && held.validity().overlaps(window)) {
                throw new IllegalArgumentException(
                        ("principal %s already holds a seat in tenant %s over %s. Two concurrent "
                                        + "assignments bill the same person twice and flatter "
                                        + "every per-seat usage figure")
                                .formatted(principal, id, held.validity()));
            }
        }

        long concurrent = seats.stream()
                .filter(held -> held.validity().overlaps(window))
                .count();
        if (concurrent >= subscription.seats()) {
            throw new SeatsExhaustedException(
                    id.value().toString(), subscription.seats(), concurrent + 1);
        }

        seats.add(new SeatAssignment(principal, window));
    }

    /** Who held a seat at that instant. The billable fact. */
    public List<SeatAssignment> seatsHeldAt(AsOf at) {
        return seats.stream().filter(seat -> seat.heldAt(at)).toList();
    }

    public boolean holdsSeatAt(PrincipalId principal, AsOf at) {
        return seats.stream()
                .anyMatch(seat -> seat.principal().equals(principal) && seat.heldAt(at));
    }

    // -- lifecycle ----------------------------------------------------------

    public void suspend(String reason) {
        requireOpen("suspend");
        this.status = TenantStatus.SUSPENDED;
        this.statusReason = Objects.requireNonNull(reason, "suspension reason");
    }

    public void reinstate() {
        if (status != TenantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "tenant %s is %s and is not suspended".formatted(id, status));
        }
        this.status = TenantStatus.ACTIVE;
        this.statusReason = null;
    }

    public void close(String reason) {
        this.status = TenantStatus.CLOSED;
        this.statusReason = reason;
    }

    private void requireOpen(String action) {
        if (status == TenantStatus.CLOSED) {
            throw new IllegalStateException(
                    "cannot %s: tenant %s is closed".formatted(action, id));
        }
    }

    /** Restores persisted state without replaying the transitions that produced it. */
    public void rehydrate(
            TenantStatus status,
            String statusReason,
            List<Subscription> persistedSubscriptions,
            List<SeatAssignment> persistedSeats) {
        this.status = Objects.requireNonNull(status, "status");
        this.statusReason = statusReason;
        subscriptions.clear();
        subscriptions.addAll(persistedSubscriptions);
        seats.clear();
        seats.addAll(persistedSeats);
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public TenantStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public List<Subscription> subscriptions() {
        return List.copyOf(subscriptions);
    }

    public List<SeatAssignment> seatAssignments() {
        return List.copyOf(seats);
    }

    public boolean mayStartRuns() {
        return status.permitsUse();
    }
}
