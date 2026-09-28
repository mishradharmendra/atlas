package com.atlas.metering.adapter.out.persistence;

import com.atlas.metering.domain.model.BudgetPolicy;
import com.atlas.metering.domain.port.BudgetPolicyRepository;
import com.atlas.shared.identity.TenantId;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC store for spend ceilings. */
@Repository
class JdbcBudgetPolicyRepository implements BudgetPolicyRepository {

    private final JdbcTemplate jdbc;

    JdbcBudgetPolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BudgetPolicy> find(TenantId tenant, String period) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "select ceiling_micros, on_breach from budget_policy "
                            + "where tenant_id = ? and period = ?",
                    (rs, row) -> new BudgetPolicy(
                            tenant,
                            period,
                            rs.getLong("ceiling_micros"),
                            BudgetPolicy.BreachAction.valueOf(rs.getString("on_breach"))),
                    tenant.value(),
                    period));
        } catch (EmptyResultDataAccessException noPolicy) {
            return Optional.empty();
        }
    }

    @Override
    public void save(BudgetPolicy policy) {
        jdbc.update(
                """
                insert into budget_policy (tenant_id, period, ceiling_micros, on_breach)
                values (?, ?, ?, ?)
                on conflict (tenant_id, period)
                do update set ceiling_micros = excluded.ceiling_micros,
                              on_breach = excluded.on_breach
                """,
                policy.tenant().value(),
                policy.period(),
                policy.ceilingMicros(),
                policy.onBreach().name());
    }
}
