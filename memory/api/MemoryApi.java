package com.atlas.memory.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** What an agent may write down and read back. */
public interface MemoryApi {

    /** Returns the id of the entry written. */
    String remember(NewMemory command);

    /**
     * What this caller may be reminded of, now.
     *
     * <p>Takes the permitted tags explicitly rather than reading them from a context, so the
     * caller cannot accidentally recall under wider permissions than it verified.
     */
    List<MemoryView> recall(String tenantId, Set<String> permittedTags, Instant at, int limit);

    /** Withdraws every entry that cited a retracted document. Returns how many. */
    int forget(Set<String> docIds, String reason);
}
