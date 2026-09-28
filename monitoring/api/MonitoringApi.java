package com.atlas.monitoring.api;

import com.atlas.shared.identity.TenantId;
import java.util.List;

/** The monitoring module's inbound port. */
public interface MonitoringApi {

    /** Creates a watchlist with its materiality rule, inactive until activated. */
    WatchlistView create(NewWatchlist command);

    /** Starts monitoring. Refuses a watchlist with no materiality rule. */
    WatchlistView activate(String watchlistId);

    WatchlistView deactivate(String watchlistId);

    List<WatchlistView> forTenant(TenantId tenant);

    /** Everything this watchlist raised, delivered or not. */
    List<TriggerView> triggers(String watchlistId, int limit);

    /** Marks pending triggers as sent. Returns how many. */
    int deliverPending(String watchlistId, int limit);
}
