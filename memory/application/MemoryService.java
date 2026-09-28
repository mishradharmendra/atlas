package com.atlas.memory.application;

import com.atlas.memory.api.MemoryApi;
import com.atlas.memory.api.MemoryView;
import com.atlas.memory.api.NewMemory;
import com.atlas.memory.domain.model.MemoryEntry;
import com.atlas.memory.domain.model.MemoryKind;
import com.atlas.memory.domain.port.MemoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MemoryService implements MemoryApi {

    /**
     * How long a conclusion is repeated without being re-established.
     *
     * <p>A quarter, because that is the cadence at which the underlying filings change. An
     * unexpiring conclusion is the failure this bounds: drawn from evidence available in March,
     * repeated in September as though it were about September.
     */
    private static final Duration CONCLUSION_LIFETIME = Duration.ofDays(90);

    /** Recall is bounded here, not by the caller: an unbounded recall is an unbounded prompt. */
    private static final int MAX_RECALL = 50;

    private final MemoryRepository entries;
    private final Clock clock;

    MemoryService(MemoryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String remember(NewMemory command) {
        MemoryKind kind = MemoryKind.valueOf(command.kind());
        Instant now = clock.instant();
        MemoryEntry entry = new MemoryEntry(
                "mem-" + UUID.randomUUID(),
                command.tenantId(),
                kind,
                command.text(),
                command.writtenByRun(),
                now,
                command.sourceDocIds(),
                command.entitlementTags(),
                expiryFor(kind, command.expiresAt(), now));
        entries.save(entry);
        return entry.entryId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryView> recall(
            String tenantId, Set<String> permittedTags, Instant at, int limit) {

        Instant moment = at == null ? clock.instant() : at;
        return entries.liveFor(tenantId, moment, Math.clamp(limit, 1, MAX_RECALL)).stream()
                .filter(entry -> entry.isReadableAt(moment, tenantId, permittedTags))
                .map(MemoryService::view)
                .toList();
    }

    @Override
    @Transactional
    public int forget(Set<String> docIds, String reason) {
        List<MemoryEntry> affected = entries.citingAnyOf(docIds);
        int withdrawn = 0;
        for (MemoryEntry entry : affected) {
            if (!entry.isWithdrawn()) {
                entry.withdraw(reason);
                entries.save(entry);
                withdrawn++;
            }
        }
        return withdrawn;
    }

    private static Instant expiryFor(MemoryKind kind, Instant requested, Instant now) {
        if (requested != null) {
            return requested;
        }
        // A caller that forgot to set one gets the safe answer rather than "forever".
        return kind == MemoryKind.PRIOR_CONCLUSION ? now.plus(CONCLUSION_LIFETIME) : null;
    }

    private static MemoryView view(MemoryEntry entry) {
        return new MemoryView(
                entry.entryId(),
                entry.kind().name(),
                entry.text(),
                entry.writtenByRun(),
                entry.writtenAt(),
                List.copyOf(entry.sourceDocIds()),
                entry.expiresAt());
    }
}
