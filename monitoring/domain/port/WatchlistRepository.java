package com.atlas.monitoring.domain.port;

import com.atlas.monitoring.domain.model.Watchlist;
import com.atlas.monitoring.domain.model.WatchlistId;
import com.atlas.shared.identity.TenantId;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository {

    void save(Watchlist watchlist);

    Optional<Watchlist> findById(WatchlistId id);

    /** Active watchlists that name this subject, across every tenant. */
    List<Watchlist> watching(String subjectId);

    List<Watchlist> forTenant(TenantId tenant);
}
