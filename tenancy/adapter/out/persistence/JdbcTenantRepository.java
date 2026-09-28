package com.atlas.tenancy.adapter.out.persistence;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.Validity;
import com.atlas.tenancy.domain.model.SeatAssignment;
import com.atlas.tenancy.domain.model.Subscription;
import com.atlas.tenancy.domain.model.Tenant;
import com.atlas.tenancy.domain.model.TenantStatus;
import com.atlas.tenancy.domain.port.TenantRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC store for tenancy.
 *
 * <p>Subscriptions and seats are replaced wholesale on save rather than diffed. Both sets are
 * small — bounded by how many contract periods and staff a customer has — and a diff would have
 * to reproduce the overlap rules to decide what is an update and what is a new window, which is a
 * third implementation of an invariant that already exists in the aggregate and in the schema.
 */
@Repository
class JdbcTenantRepository implements TenantRepository {

    private final JdbcTemplate jdbc;

    JdbcTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "select name, created_at, status, status_reason from tenant where tenant_id = ?",
                    id.value());

            Tenant tenant = new Tenant(
                    id,
                    (String) row.get("name"),
                    ((Timestamp) row.get("created_at")).toInstant());

            tenant.rehydrate(
                    TenantStatus.valueOf((String) row.get("status")),
                    (String) row.get("status_reason"),
                    subscriptionsOf(id),
                    seatsOf(id));
            return Optional.of(tenant);
        } catch (EmptyResultDataAccessException notFound) {
            return Optional.empty();
        }
    }

    private List<Subscription> subscriptionsOf(TenantId id) {
        return jdbc.query(
                """
                select plan_id, seats, seat_price_micros, trial, valid_from, valid_to
                from tenant_subscription where tenant_id = ? order by valid_from
                """,
                (rs, row) -> new Subscription(
                        rs.getString("plan_id"),
                        rs.getInt("seats"),
                        rs.getLong("seat_price_micros"),
                        new Validity(
                                rs.getTimestamp("valid_from").toInstant(),
                                instantOrNull(rs.getTimestamp("valid_to"))),
                        rs.getBoolean("trial")),
                id.value());
    }

    private List<SeatAssignment> seatsOf(TenantId id) {
        return jdbc.query(
                """
                select principal_id, valid_from, valid_to
                from tenant_seat where tenant_id = ? order by valid_from
                """,
                (rs, row) -> new SeatAssignment(
                        new PrincipalId(UUID.fromString(rs.getString("principal_id"))),
                        new Validity(
                                rs.getTimestamp("valid_from").toInstant(),
                                instantOrNull(rs.getTimestamp("valid_to")))),
                id.value());
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    @Transactional
    public void save(Tenant tenant) {
        jdbc.update(
                """
                insert into tenant (tenant_id, name, created_at, status, status_reason)
                values (?, ?, ?, ?, ?)
                on conflict (tenant_id)
                do update set name = excluded.name,
                              status = excluded.status,
                              status_reason = excluded.status_reason
                """,
                tenant.id().value(),
                tenant.name(),
                Timestamp.from(tenant.createdAt()),
                tenant.status().name(),
                tenant.statusReason());

        jdbc.update("delete from tenant_subscription where tenant_id = ?", tenant.id().value());
        for (Subscription subscription : tenant.subscriptions()) {
            jdbc.update(
                    """
                    insert into tenant_subscription
                        (tenant_id, plan_id, seats, seat_price_micros, trial, valid_from, valid_to)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    tenant.id().value(),
                    subscription.planId(),
                    subscription.seats(),
                    subscription.seatPriceMicros(),
                    subscription.trial(),
                    Timestamp.from(subscription.validity().from()),
                    subscription.validity().to() == null
                            ? null
                            : Timestamp.from(subscription.validity().to()));
        }

        jdbc.update("delete from tenant_seat where tenant_id = ?", tenant.id().value());
        for (SeatAssignment seat : tenant.seatAssignments()) {
            jdbc.update(
                    """
                    insert into tenant_seat (tenant_id, principal_id, valid_from, valid_to)
                    values (?, ?, ?, ?)
                    """,
                    tenant.id().value(),
                    seat.principal().value(),
                    Timestamp.from(seat.validity().from()),
                    seat.validity().to() == null ? null : Timestamp.from(seat.validity().to()));
        }
    }
}
