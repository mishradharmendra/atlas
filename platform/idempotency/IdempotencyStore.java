package com.atlas.platform.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String tenantId, String key);

    /**
     * Stores a completed response, or reports that another request got there first.
     *
     * <p>Returns false rather than throwing on a race: two concurrent retries of the same call is
     * exactly the situation this exists for, and the loser's job is to serve the winner's
     * response, not to fail.
     */
    boolean saveIfAbsent(IdempotencyRecord record);

    /** Removes records older than the retry window. Returns how many. */
    int expireBefore(Instant cutoff);
}
