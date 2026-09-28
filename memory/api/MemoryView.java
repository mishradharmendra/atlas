package com.atlas.memory.api;

import java.time.Instant;
import java.util.List;

/** A memory entry as a caller sees it, with the provenance that makes it repeatable. */
public record MemoryView(
        String entryId,
        String kind,
        String text,
        String writtenByRun,
        Instant writtenAt,
        List<String> sourceDocIds,
        Instant expiresAt) {}
