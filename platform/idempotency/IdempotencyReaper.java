package com.atlas.platform.idempotency;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops idempotency keys past the retry window.
 *
 * <p>A key is a promise about a retry, not an audit record. Keeping them forever grows a table
 * that every write touches, and the guarantee it would preserve — that a request sent a year
 * apart is deduplicated — is not one anybody wants.
 */
@Component
class IdempotencyReaper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyReaper.class);

    private final IdempotencyStore store;
    private final Clock clock;

    IdempotencyReaper(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    int expire() {
        int removed = store.expireBefore(clock.instant().minus(IdempotencyFilter.RETENTION));
        if (removed > 0) {
            log.info("expired {} idempotency key(s) past the {} window", removed, IdempotencyFilter.RETENTION);
        }
        return removed;
    }
}
