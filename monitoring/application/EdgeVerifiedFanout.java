package com.atlas.monitoring.application;

import com.atlas.knowledge.events.EdgeVerified;
import com.atlas.monitoring.domain.model.SuppressionReason;
import com.atlas.monitoring.domain.model.Trigger;
import com.atlas.monitoring.domain.model.TriggerId;
import com.atlas.monitoring.domain.model.Watchlist;
import com.atlas.monitoring.domain.port.TriggerRepository;
import com.atlas.monitoring.domain.port.WatchlistRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns verified facts into triggers for whoever asked to hear about them.
 *
 * <p>Every decision here is a decision not to send something, and each one is recorded.
 */
@Component
class EdgeVerifiedFanout {

    private static final Logger log = LoggerFactory.getLogger(EdgeVerifiedFanout.class);

    private final WatchlistRepository watchlists;
    private final TriggerRepository triggers;

    EdgeVerifiedFanout(WatchlistRepository watchlists, TriggerRepository triggers) {
        this.watchlists = watchlists;
        this.triggers = triggers;
    }

    @ApplicationModuleListener
    void on(EdgeVerified event) {
        for (Watchlist watchlist : watchlists.watching(event.subjectId())) {
            if (!watchlist.wants(event.subjectId(), event.relationshipType())) {
                // Not recorded as a suppressed trigger: this list never asked about this kind of
                // change, so there is nothing it was owed. Storing these would bury the
                // suppressions that do mean a threshold is wrong.
                continue;
            }
            raise(watchlist, event);
        }
    }

    private void raise(Watchlist watchlist, EdgeVerified event) {
        String factKey = factKey(event);

        Trigger trigger = new Trigger(
                TriggerId.of(UUID.randomUUID().toString()),
                watchlist.id(),
                watchlist.tenant(),
                event.subjectId(),
                event.relationshipType(),
                factKey,
                "%s %s %s".formatted(event.subjectId(), event.relationshipType(), event.objectId()),
                // The event's own instant, never the clock. A backfill replayed through this
                // listener would otherwise raise a week of alerts all looking like they happened
                // this morning.
                event.at());

        SuppressionReason reason = suppressionFor(watchlist, trigger, event.at());
        if (reason != null) {
            trigger.suppress(reason);
        }

        if (!triggers.saveIfNew(trigger)) {
            log.debug(
                    "fact {} already recorded on watchlist {}; redelivery ignored",
                    factKey,
                    watchlist.id());
        }
    }

    /** The three checks, in the order that keeps each one's counts meaningful. */
    private SuppressionReason suppressionFor(Watchlist watchlist, Trigger trigger, Instant at) {
        // Exact and permanent: one real-world fact, one alert, forever.
        if (triggers.hasFact(watchlist.id(), trigger.factKey())) {
            return SuppressionReason.DUPLICATE;
        }

        // Repetition. Different facts, same story, told twice within the window.
        Instant quietSince = at.minus(watchlist.rule().quietPeriod());
        if (!watchlist.rule().quietPeriod().isZero()
                && triggers.alertedSince(
                        watchlist.id(), trigger.subjectId(), trigger.changeKind(), quietSince)) {
            return SuppressionReason.QUIET_PERIOD;
        }

        // Volume. Counted against what was actually delivered, not against everything raised:
        // counting suppressions towards the cap would let a flood of duplicates exhaust the
        // budget and silence the one genuine alert behind them.
        Instant windowStart = at.minus(watchlist.rule().window());
        if (triggers.deliveredSince(watchlist.id(), windowStart) >= watchlist.rule().maxPerWindow()) {
            return SuppressionReason.FANNED_OUT;
        }

        return null;
    }

    private static String factKey(EdgeVerified event) {
        // The fact, not the document that carried it. Five outlets reporting one acquisition is
        // one alert; keyed on the document it would be five, and the watchlist gets muted.
        return "%s|%s|%s".formatted(event.subjectId(), event.relationshipType(), event.objectId());
    }
}
