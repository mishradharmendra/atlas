package com.atlas.monitoring.adapter.out.persistence;

import com.atlas.monitoring.domain.model.SuppressionReason;
import com.atlas.monitoring.domain.model.Trigger;
import com.atlas.monitoring.domain.model.TriggerId;
import com.atlas.monitoring.domain.model.TriggerStatus;
import com.atlas.monitoring.domain.model.WatchlistId;
import com.atlas.monitoring.domain.port.TriggerRepository;
import com.atlas.shared.identity.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTriggerRepository implements TriggerRepository {

    private final JdbcTemplate jdbc;

    JdbcTriggerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfNew(Trigger trigger) {
        try {
            insert(trigger);
            return true;
        } catch (DuplicateKeyException alreadyRecorded) {
            // Redelivery of the same domain event, which the outbox guarantees can happen. Not
            // an error: the watcher has already been told this fact, which is the outcome wanted.
            return false;
        }
    }

    @Override
    public void save(Trigger trigger) {
        int updated = jdbc.update(
                """
                UPDATE monitoring_trigger
                SET status = ?, suppression_reason = ?, delivered_at = ?
                WHERE trigger_id = ?
                """,
                trigger.status().name(),
                trigger.suppressionReason() == null ? null : trigger.suppressionReason().name(),
                trigger.deliveredAt() == null ? null : Timestamp.from(trigger.deliveredAt()),
                trigger.id().value());
        if (updated == 0) {
            insert(trigger);
        }
    }

    @Override
    public Optional<Trigger> findById(TriggerId id) {
        return jdbc
                .query(
                        "SELECT * FROM monitoring_trigger WHERE trigger_id = ?",
                        this::toTrigger,
                        id.value())
                .stream()
                .findFirst();
    }

    @Override
    public boolean hasFact(WatchlistId watchlist, String factKey) {
        Integer count = jdbc.queryForObject(
                """
                SELECT count(*) FROM monitoring_trigger
                WHERE watchlist_id = ? AND fact_key = ?
                """,
                Integer.class,
                watchlist.value(),
                factKey);
        return count != null && count > 0;
    }

    @Override
    public boolean alertedSince(
            WatchlistId watchlist, String subjectId, String changeKind, Instant since) {
        // Counts what was actually sent. A trigger suppressed as a duplicate did not interrupt
        // anybody, so letting it start a quiet period would silence the next genuine change.
        Integer count = jdbc.queryForObject(
                """
                SELECT count(*) FROM monitoring_trigger
                WHERE watchlist_id = ? AND subject_id = ? AND change_kind = ?
                  AND observed_at >= ? AND status <> 'SUPPRESSED'
                """,
                Integer.class,
                watchlist.value(),
                subjectId,
                changeKind,
                Timestamp.from(since));
        return count != null && count > 0;
    }

    @Override
    public int deliveredSince(WatchlistId watchlist, Instant since) {
        Integer count = jdbc.queryForObject(
                """
                SELECT count(*) FROM monitoring_trigger
                WHERE watchlist_id = ? AND observed_at >= ? AND status <> 'SUPPRESSED'
                """,
                Integer.class,
                watchlist.value(),
                Timestamp.from(since));
        return count == null ? 0 : count;
    }

    @Override
    public List<Trigger> pendingFor(WatchlistId watchlist, int limit) {
        return jdbc.query(
                """
                SELECT * FROM monitoring_trigger
                WHERE watchlist_id = ? AND status = 'PENDING'
                ORDER BY observed_at
                LIMIT ?
                """,
                this::toTrigger,
                watchlist.value(),
                limit);
    }

    @Override
    public List<Trigger> forWatchlist(WatchlistId watchlist, int limit) {
        return jdbc.query(
                """
                SELECT * FROM monitoring_trigger
                WHERE watchlist_id = ?
                ORDER BY observed_at DESC
                LIMIT ?
                """,
                this::toTrigger,
                watchlist.value(),
                limit);
    }

    private void insert(Trigger trigger) {
        jdbc.update(
                """
                INSERT INTO monitoring_trigger (trigger_id, watchlist_id, tenant_id, subject_id,
                                                change_kind, fact_key, summary, observed_at,
                                                status, suppression_reason, delivered_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                trigger.id().value(),
                trigger.watchlist().value(),
                trigger.tenant().value(),
                trigger.subjectId(),
                trigger.changeKind(),
                trigger.factKey(),
                trigger.summary(),
                Timestamp.from(trigger.observedAt()),
                trigger.status().name(),
                trigger.suppressionReason() == null ? null : trigger.suppressionReason().name(),
                trigger.deliveredAt() == null ? null : Timestamp.from(trigger.deliveredAt()));
    }

    private Trigger toTrigger(ResultSet rs, int rowNum) throws SQLException {
        Trigger trigger = new Trigger(
                TriggerId.of(rs.getString("trigger_id")),
                WatchlistId.of(rs.getString("watchlist_id")),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getString("subject_id"),
                rs.getString("change_kind"),
                rs.getString("fact_key"),
                rs.getString("summary"),
                rs.getTimestamp("observed_at").toInstant());

        String reason = rs.getString("suppression_reason");
        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        trigger.rehydrate(
                TriggerStatus.valueOf(rs.getString("status")),
                reason == null ? null : SuppressionReason.valueOf(reason),
                deliveredAt == null ? null : deliveredAt.toInstant());
        return trigger;
    }
}
