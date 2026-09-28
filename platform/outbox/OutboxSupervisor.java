package com.atlas.platform.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the event outbox moving, and makes it loud when it is not.
 *
 * <h2>The failure this exists for</h2>
 *
 * <p>A listener that throws leaves its publication incomplete. The event is not lost — that is
 * the whole point of the outbox — but nothing retries it either, so the cascade silently does not
 * happen: a retracted document stays indexed, a verified edge never reaches a watchlist, a step's
 * cost is never metered. Every one of those looks healthy from the outside, because the write
 * that triggered them succeeded.
 *
 * <h2>Why resubmission alone would be worse than nothing</h2>
 *
 * <p>An event that failed forty times will fail the forty-first, and a supervisor that quietly
 * retries forever converts a loud bug into a permanent one. So resubmission is paired with an
 * age check: past {@link #POISON_AGE} a publication is not a hiccup, and it is reported as
 * something a person has to look at.
 */
@Component
class OutboxSupervisor {

    private static final Logger log = LoggerFactory.getLogger(OutboxSupervisor.class);

    /**
     * How long a publication may be in flight before resubmission considers it stuck.
     *
     * <p>Resubmitting one that is merely slow runs the listener twice. Listeners here are
     * idempotent by design, but "it is safe to do twice" is not a reason to do it twice.
     */
    private static final Duration IN_FLIGHT_GRACE = Duration.ofMinutes(2);

    /** Past this, a publication is not going to succeed by being asked again. */
    private static final Duration POISON_AGE = Duration.ofHours(1);

    /** Completed publications are evidence, not state. Kept long enough to investigate with. */
    private static final Duration COMPLETED_RETENTION = Duration.ofDays(7);

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final JdbcTemplate jdbc;

    OutboxSupervisor(
            IncompleteEventPublications incomplete,
            CompletedEventPublications completed,
            JdbcTemplate jdbc) {
        this.incomplete = incomplete;
        this.completed = completed;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "PT5M")
    void resubmitStuckPublications() {
        incomplete.resubmitIncompletePublicationsOlderThan(IN_FLIGHT_GRACE);
    }

    /**
     * Reports publications that resubmission is not fixing.
     *
     * <p>Grouped by event type and listener, because the two questions are "what is broken" and
     * "whose listener is breaking it", and a bare count answers neither.
     */
    @Scheduled(fixedDelayString = "PT15M")
    void reportPoisonedPublications() {
        List<Map<String, Object>> stuck = jdbc.queryForList(
                """
                SELECT event_type, listener_id, count(*) AS stuck_count
                FROM event_publication
                WHERE completion_date IS NULL
                  AND publication_date < now() - CAST(? AS interval)
                GROUP BY event_type, listener_id
                ORDER BY stuck_count DESC
                """,
                POISON_AGE.toSeconds() + " seconds");

        for (Map<String, Object> row : stuck) {
            log.warn(
                    "{} event publication(s) of {} to {} have been incomplete for over {}; "
                            + "resubmission is not clearing them and the cascade behind them has "
                            + "not run",
                    row.get("stuck_count"),
                    row.get("event_type"),
                    row.get("listener_id"),
                    POISON_AGE);
        }
    }

    @Scheduled(fixedDelayString = "PT6H")
    void purgeCompletedPublications() {
        // An unbounded outbox makes every publishing write slower, and the rows it accumulates
        // are ones whose listeners already ran.
        completed.deletePublicationsOlderThan(COMPLETED_RETENTION);
    }
}
