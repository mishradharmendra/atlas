package com.atlas.monitoring.application;

import com.atlas.monitoring.api.MonitoringApi;
import com.atlas.monitoring.api.NewWatchlist;
import com.atlas.monitoring.api.TriggerView;
import com.atlas.monitoring.api.WatchlistView;
import com.atlas.monitoring.domain.model.MaterialityRule;
import com.atlas.monitoring.domain.model.Trigger;
import com.atlas.monitoring.domain.model.Watchlist;
import com.atlas.monitoring.domain.model.WatchlistId;
import com.atlas.monitoring.domain.port.TriggerRepository;
import com.atlas.monitoring.domain.port.WatchlistRepository;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitoringService implements MonitoringApi {

    private final WatchlistRepository watchlists;
    private final TriggerRepository triggers;
    private final Clock clock;

    MonitoringService(
            WatchlistRepository watchlists, TriggerRepository triggers, Clock clock) {
        this.watchlists = watchlists;
        this.triggers = triggers;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WatchlistView create(NewWatchlist command) {
        Watchlist watchlist = new Watchlist(
                WatchlistId.of(UUID.randomUUID().toString()),
                TenantId.of(command.tenantId()),
                PrincipalId.of(command.principalId()),
                command.name(),
                command.subjectIds(),
                clock.instant());

        watchlist.applyRule(new MaterialityRule(
                command.relationshipTypes(),
                Duration.ofSeconds(command.quietPeriodSeconds()),
                command.maxPerWindow(),
                Duration.ofSeconds(command.windowSeconds())));

        watchlists.save(watchlist);
        return view(watchlist);
    }

    @Override
    @Transactional
    public WatchlistView activate(String watchlistId) {
        Watchlist watchlist = load(watchlistId);
        watchlist.activate();
        watchlists.save(watchlist);
        return view(watchlist);
    }

    @Override
    @Transactional
    public WatchlistView deactivate(String watchlistId) {
        Watchlist watchlist = load(watchlistId);
        watchlist.deactivate();
        watchlists.save(watchlist);
        return view(watchlist);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistView> forTenant(TenantId tenant) {
        return watchlists.forTenant(tenant).stream().map(MonitoringService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TriggerView> triggers(String watchlistId, int limit) {
        return triggers.forWatchlist(WatchlistId.of(watchlistId), limit).stream()
                .map(MonitoringService::view)
                .toList();
    }

    @Override
    @Transactional
    public int deliverPending(String watchlistId, int limit) {
        List<Trigger> pending = triggers.pendingFor(WatchlistId.of(watchlistId), limit);
        for (Trigger trigger : pending) {
            trigger.deliver(clock.instant());
            triggers.save(trigger);
        }
        return pending.size();
    }

    private Watchlist load(String watchlistId) {
        return watchlists
                .findById(WatchlistId.of(watchlistId))
                .orElseThrow(() -> new IllegalArgumentException("no watchlist " + watchlistId));
    }

    private static WatchlistView view(Watchlist watchlist) {
        MaterialityRule rule = watchlist.rule();
        return new WatchlistView(
                watchlist.id().value(),
                watchlist.tenant().value().toString(),
                watchlist.owner().value().toString(),
                watchlist.name(),
                watchlist.subjectIds(),
                rule == null ? null : rule.relationshipTypes(),
                rule == null ? 0 : rule.quietPeriod().toSeconds(),
                rule == null ? 0 : rule.maxPerWindow(),
                rule == null ? 0 : rule.window().toSeconds(),
                watchlist.isActive());
    }

    private static TriggerView view(Trigger trigger) {
        return new TriggerView(
                trigger.id().value(),
                trigger.watchlist().value(),
                trigger.subjectId(),
                trigger.changeKind(),
                trigger.factKey(),
                trigger.summary(),
                trigger.observedAt(),
                trigger.status().name(),
                trigger.suppressionReason() == null ? null : trigger.suppressionReason().name(),
                trigger.deliveredAt());
    }
}
