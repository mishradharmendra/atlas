package com.atlas.metering.adapter.out.persistence;

import com.atlas.metering.domain.model.TenantUsage;
import com.atlas.metering.domain.model.UsageRecord;
import com.atlas.metering.domain.port.UsageLedger;
import com.atlas.shared.identity.TenantId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC ledger.
 *
 * <p>Plain SQL rather than JPA. This is an append-only table and a group-by; an ORM would add a
 * persistence context that has to be flushed and an entity that could be mutated, both of which
 * are the opposite of what a ledger wants.
 */
@Repository
class JdbcUsageLedger implements UsageLedger {

    private static final DateTimeFormatter PERIOD =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    JdbcUsageLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UsageRecord usage) {
        try {
            jdbc.update(
                    """
                    insert into usage_record
                        (run_id, step_id, attempt, tenant_id, tokens, cost_micros, incurred_at, period)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    usage.runId(),
                    usage.stepId(),
                    usage.attempt(),
                    usage.tenant().value(),
                    usage.tokens(),
                    usage.costMicros(),
                    java.sql.Timestamp.from(usage.incurredAt()),
                    PERIOD.format(usage.incurredAt()));
        } catch (DuplicateKeyException redelivered) {
            // The same spend seen twice. Swallowed rather than surfaced: event redelivery is
            // routine, and a ledger that failed on it would turn normal operation into an alert.
        }
    }

    @Override
    public long spentMicros(TenantId tenant, String period) {
        Long spent = jdbc.queryForObject(
                "select coalesce(sum(cost_micros), 0) from usage_record "
                        + "where tenant_id = ? and period = ?",
                Long.class,
                tenant.value(),
                period);
        return spent == null ? 0L : spent;
    }

    @Override
    public TenantUsage usageFor(TenantId tenant, String period) {
        Map<String, Object> totals = jdbc.queryForMap(
                """
                select coalesce(count(distinct run_id), 0) as runs,
                       coalesce(sum(tokens), 0)            as tokens,
                       coalesce(sum(cost_micros), 0)       as cost
                from usage_record
                where tenant_id = ? and period = ?
                """,
                tenant.value(),
                period);

        return new TenantUsage(
                tenant.value().toString(),
                period,
                0,
                0,
                ((Number) totals.get("runs")).longValue(),
                ((Number) totals.get("tokens")).longValue(),
                ((Number) totals.get("cost")).longValue());
    }
}
