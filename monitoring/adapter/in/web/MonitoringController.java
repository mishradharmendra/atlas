package com.atlas.monitoring.adapter.in.web;

import com.atlas.monitoring.api.MonitoringApi;
import com.atlas.monitoring.api.NewWatchlist;
import com.atlas.monitoring.api.TriggerView;
import com.atlas.monitoring.api.WatchlistView;
import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import com.atlas.shared.identity.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitoring")
class MonitoringController {

    private final MonitoringApi monitoring;

    MonitoringController(MonitoringApi monitoring) {
        this.monitoring = monitoring;
    }

    /** Tenant and owner come from the verified credential, never from the body. */
    @PostMapping(value = "/watchlists", produces = MediaType.APPLICATION_JSON_VALUE)
    WatchlistView create(@RequestBody NewWatchlistRequest body) {
        RequestTenant caller = TenantContext.require();
        return monitoring.create(new NewWatchlist(
                caller.tenantId(),
                caller.principalId(),
                body.name(),
                body.subjectIds(),
                body.relationshipTypes(),
                body.quietPeriodSeconds(),
                body.maxPerWindow(),
                body.windowSeconds()));
    }

    @PostMapping(
            value = "/watchlists/{watchlistId}/activation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    WatchlistView activate(@PathVariable String watchlistId) {
        TenantContext.require();
        return monitoring.activate(watchlistId);
    }

    @PostMapping(
            value = "/watchlists/{watchlistId}/deactivation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    WatchlistView deactivate(@PathVariable String watchlistId) {
        TenantContext.require();
        return monitoring.deactivate(watchlistId);
    }

    @GetMapping(value = "/watchlists", produces = MediaType.APPLICATION_JSON_VALUE)
    List<WatchlistView> mine() {
        RequestTenant caller = TenantContext.require();
        return monitoring.forTenant(TenantId.of(caller.tenantId()));
    }

    /** Everything raised, delivered or not: the answer to "why was I not told". */
    @GetMapping(
            value = "/watchlists/{watchlistId}/triggers",
            produces = MediaType.APPLICATION_JSON_VALUE)
    List<TriggerView> triggers(
            @PathVariable String watchlistId, @RequestParam(defaultValue = "50") int limit) {
        TenantContext.require();
        return monitoring.triggers(watchlistId, limit);
    }

    @PostMapping(
            value = "/watchlists/{watchlistId}/delivery",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Integer> deliver(
            @PathVariable String watchlistId, @RequestParam(defaultValue = "50") int limit) {
        TenantContext.require();
        return Map.of("delivered", monitoring.deliverPending(watchlistId, limit));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> refused(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    record NewWatchlistRequest(
            String name,
            Set<String> subjectIds,
            Set<String> relationshipTypes,
            long quietPeriodSeconds,
            int maxPerWindow,
            long windowSeconds) {}
}
