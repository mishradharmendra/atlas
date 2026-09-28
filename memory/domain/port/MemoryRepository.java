package com.atlas.memory.domain.port;

import com.atlas.memory.domain.model.MemoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface MemoryRepository {

    void save(MemoryEntry entry);

    /**
     * Live entries for a tenant at a moment.
     *
     * <p>The entitlement filter is applied by the caller on the returned entries rather than
     * pushed into SQL, because "permitted tags contain every tag this entry carries" is a subset
     * test the domain owns. What SQL does here is narrow by tenant, expiry and withdrawal — the
     * three conditions that are cheap to index and that no caller may override.
     */
    List<MemoryEntry> liveFor(String tenantId, Instant at, int limit);

    /** Every entry naming any of these documents, withdrawn or not. */
    List<MemoryEntry> citingAnyOf(Set<String> docIds);
}
