package com.atlas.monitoring.domain.port;

import com.atlas.monitoring.domain.model.Trigger;
import com.atlas.monitoring.domain.model.TriggerId;
import com.atlas.monitoring.domain.model.WatchlistId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TriggerRepository {

    /**
     * Stores a trigger, or reports that this fact was already recorded on this watchlist.
     *
     * <p>Returns false on a duplicate rather than throwing, because redelivery of the same domain
     * event is routine rather than exceptional — the outbox guarantees at-least-once.
     */
    boolean saveIfNew(Trigger trigger);

    void save(Trigger trigger);

    Optional<Trigger> findById(TriggerId id);

    /** Whether this exact fact has already reached this watchlist. */
    boolean hasFact(WatchlistId watchlist, String factKey);

    /** Whether this subject and kind of change alerted since the given instant. */
    boolean alertedSince(WatchlistId watchlist, String subjectId, String changeKind, Instant since);

    /** How many triggers this watchlist has actually sent since the given instant. */
    int deliveredSince(WatchlistId watchlist, Instant since);

    List<Trigger> pendingFor(WatchlistId watchlist, int limit);

    List<Trigger> forWatchlist(WatchlistId watchlist, int limit);
}
