package com.atlas.research.application;

import com.atlas.research.domain.model.Run;
import com.atlas.research.domain.port.RunRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds runs whose executor stopped saying it was alive, and stops pretending they are running.
 *
 * <h2>Why abandoning is better than leaving them</h2>
 *
 * <p>A run stuck {@code RUNNING} is the worst of the available states: it delivers nothing, it
 * holds its budget reserved, and it raises no error, so the only way anyone learns about it is a
 * user asking why their question never came back. An orphan that is abandoned with a reason is at
 * least a fact somebody can act on.
 *
 * <p>The grace period exists because a lapsed lease is not proof of death. An executor paused by
 * a long garbage collection or a slow model call is alive and will renew shortly, and abandoning
 * its run would throw away work that was about to finish. So the first lapse only makes the run
 * reclaimable; abandonment waits until it has been lapsed for long enough that no live executor
 * would still be silent.
 */
@Component
class OrphanedRunReaper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedRunReaper.class);

    private final RunRepository runs;
    private final Clock clock;
    private final Duration grace;
    private final int batchSize;

    OrphanedRunReaper(
            RunRepository runs,
            Clock clock,
            @Value("${atlas.research.orphan-grace:PT15M}") Duration grace,
            @Value("${atlas.research.orphan-batch:100}") int batchSize) {
        this.runs = runs;
        this.clock = clock;
        this.grace = grace;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${atlas.research.orphan-sweep:PT1M}")
    void sweep() {
        reap();
    }

    /**
     * One sweep. Returns how many runs it abandoned.
     *
     * <p>Package-private and returning a count so a test can drive it directly rather than
     * waiting for a scheduler — a test that sleeps for the sweep interval is a test nobody runs.
     */
    @Transactional
    int reap() {
        List<Run> orphans = runs.orphaned(clock.instant().minus(grace), batchSize);

        for (Run orphan : orphans) {
            orphan.abandon(
                    "executor %s stopped renewing its lease; last seen %s"
                            .formatted(orphan.lease().heldBy(), orphan.lease().expiresAt()));
            runs.save(orphan);

            // Warn, not info: every one of these is a question a user asked and did not get an
            // answer to, and the count is the signal that executors are dying rather than that
            // one died.
            log.warn(
                    "abandoned orphaned run {} (executor {}, lease lapsed {})",
                    orphan.id(),
                    orphan.lease().heldBy(),
                    orphan.lease().expiresAt());
        }
        return orphans.size();
    }
}
