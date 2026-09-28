package com.atlas.monitoring.domain.model;

import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * One thing a watcher is owed, or was deliberately not sent.
 *
 * <p>Suppressed triggers are stored, not discarded. "Why was I not told about this" is the only
 * question ever asked of a monitoring system, and it is unanswerable by one that keeps no record
 * of what it decided against. Over-suppression is otherwise invisible — it looks exactly like a
 * quiet week.
 */
public class Trigger {

    private final TriggerId id;
    private final WatchlistId watchlist;
    private final TenantId tenant;
    private final String subjectId;
    private final String changeKind;
    private final String factKey;
    private final String summary;
    private final Instant observedAt;

    private TriggerStatus status = TriggerStatus.PENDING;
    private SuppressionReason suppressionReason;
    private Instant deliveredAt;

    public Trigger(
            TriggerId id,
            WatchlistId watchlist,
            TenantId tenant,
            String subjectId,
            String changeKind,
            String factKey,
            String summary,
            Instant observedAt) {

        this.id = Objects.requireNonNull(id, "trigger id");
        this.watchlist = Objects.requireNonNull(watchlist, "watchlist");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.observedAt = Objects.requireNonNull(observedAt, "observed-at");

        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("a trigger must name its subject");
        }
        if (changeKind == null || changeKind.isBlank()) {
            throw new IllegalArgumentException("a trigger must name what kind of change it is");
        }

        // The deduplication key identifies the fact, not the document that reported it. Keyed on
        // the document, one acquisition covered by five outlets is five alerts, and the watcher
        // turns the list off before the fifth arrives.
        if (factKey == null || factKey.isBlank()) {
            throw new IllegalArgumentException(
                    "a trigger must carry the key of the fact it reports, or the same event "
                            + "arriving from several sources cannot be recognised as one");
        }

        this.subjectId = subjectId;
        this.changeKind = changeKind;
        this.factKey = factKey;
        this.summary = summary;
    }

    /** Records that the watcher was told. */
    public void deliver(Instant at) {
        if (status == TriggerStatus.SUPPRESSED) {
            throw new IllegalStateException(
                    ("trigger %s was suppressed as %s and cannot then be delivered; the record of "
                                    + "what was withheld is the only evidence the thresholds are "
                                    + "wrong")
                            .formatted(id, suppressionReason));
        }
        this.status = TriggerStatus.DELIVERED;
        this.deliveredAt = at;
    }

    /** Records that the watcher was deliberately not told, and why. */
    public void suppress(SuppressionReason reason) {
        if (status == TriggerStatus.DELIVERED) {
            throw new IllegalStateException(
                    "trigger %s was already delivered; it cannot be unsent".formatted(id));
        }
        this.status = TriggerStatus.SUPPRESSED;
        this.suppressionReason = Objects.requireNonNull(reason, "suppression reason");
    }

    public TriggerId id() {
        return id;
    }

    public WatchlistId watchlist() {
        return watchlist;
    }

    public TenantId tenant() {
        return tenant;
    }

    public String subjectId() {
        return subjectId;
    }

    public String changeKind() {
        return changeKind;
    }

    public String factKey() {
        return factKey;
    }

    public String summary() {
        return summary;
    }

    public Instant observedAt() {
        return observedAt;
    }

    public TriggerStatus status() {
        return status;
    }

    public SuppressionReason suppressionReason() {
        return suppressionReason;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    /** Reconstitutes from storage. */
    public void rehydrate(
            TriggerStatus storedStatus, SuppressionReason storedReason, Instant storedDeliveredAt) {
        this.status = storedStatus;
        this.suppressionReason = storedReason;
        this.deliveredAt = storedDeliveredAt;
    }
}
