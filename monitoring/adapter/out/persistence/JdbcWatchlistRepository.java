package com.atlas.monitoring.adapter.out.persistence;

import com.atlas.monitoring.domain.model.MaterialityRule;
import com.atlas.monitoring.domain.model.Watchlist;
import com.atlas.monitoring.domain.model.WatchlistId;
import com.atlas.monitoring.domain.port.WatchlistRepository;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWatchlistRepository implements WatchlistRepository {

    private final JdbcTemplate jdbc;

    JdbcWatchlistRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Watchlist watchlist) {
        MaterialityRule rule = watchlist.rule();
        jdbc.update(
                """
                INSERT INTO watchlist (watchlist_id, tenant_id, owner_id, name, created_at,
                                       quiet_period_secs, max_per_window, window_secs, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (watchlist_id) DO UPDATE SET
                    quiet_period_secs = EXCLUDED.quiet_period_secs,
                    max_per_window    = EXCLUDED.max_per_window,
                    window_secs       = EXCLUDED.window_secs,
                    active            = EXCLUDED.active
                """,
                watchlist.id().value(),
                watchlist.tenant().value(),
                watchlist.owner().value(),
                watchlist.name(),
                Timestamp.from(watchlist.createdAt()),
                rule == null ? null : rule.quietPeriod().toSeconds(),
                rule == null ? null : rule.maxPerWindow(),
                rule == null ? null : rule.window().toSeconds(),
                watchlist.isActive());

        for (String subjectId : watchlist.subjectIds()) {
            jdbc.update(
                    """
                    INSERT INTO watchlist_subject (watchlist_id, subject_id) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    watchlist.id().value(),
                    subjectId);
        }
        if (rule != null) {
            for (String type : rule.relationshipTypes()) {
                jdbc.update(
                        """
                        INSERT INTO watchlist_relationship_type (watchlist_id, relationship_type)
                        VALUES (?, ?) ON CONFLICT DO NOTHING
                        """,
                        watchlist.id().value(),
                        type);
            }
        }
    }

    @Override
    public Optional<Watchlist> findById(WatchlistId id) {
        return jdbc
                .query("SELECT * FROM watchlist WHERE watchlist_id = ?", this::toWatchlist, id.value())
                .stream()
                .findFirst();
    }

    @Override
    public List<Watchlist> watching(String subjectId) {
        return jdbc.query(
                """
                SELECT w.* FROM watchlist w
                JOIN watchlist_subject s ON s.watchlist_id = w.watchlist_id
                WHERE s.subject_id = ? AND w.active
                """,
                this::toWatchlist,
                subjectId);
    }

    @Override
    public List<Watchlist> forTenant(TenantId tenant) {
        return jdbc.query(
                "SELECT * FROM watchlist WHERE tenant_id = ? ORDER BY created_at DESC",
                this::toWatchlist,
                tenant.value());
    }

    private Watchlist toWatchlist(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("watchlist_id");
        Set<String> subjects = new HashSet<>(jdbc.queryForList(
                "SELECT subject_id FROM watchlist_subject WHERE watchlist_id = ?",
                String.class,
                id));
        Set<String> types = new HashSet<>(jdbc.queryForList(
                "SELECT relationship_type FROM watchlist_relationship_type WHERE watchlist_id = ?",
                String.class,
                id));

        Watchlist watchlist = new Watchlist(
                WatchlistId.of(id),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new PrincipalId(rs.getObject("owner_id", UUID.class)),
                rs.getString("name"),
                subjects,
                rs.getTimestamp("created_at").toInstant());

        // Rehydrated rather than replayed through activate(): replaying would re-run the
        // has-a-rule check against a half-built object and refuse to load rows the same rule
        // permitted to be written.
        MaterialityRule rule = types.isEmpty()
                ? null
                : new MaterialityRule(
                        types,
                        Duration.ofSeconds(rs.getLong("quiet_period_secs")),
                        rs.getInt("max_per_window"),
                        Duration.ofSeconds(rs.getLong("window_secs")));
        watchlist.rehydrate(rule, rs.getBoolean("active"));
        return watchlist;
    }
}
