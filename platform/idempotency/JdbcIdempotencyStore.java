package com.atlas.platform.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String key) {
        List<IdempotencyRecord> found = jdbc.query(
                """
                SELECT * FROM idempotency_record
                WHERE tenant_id = ? AND idempotency_key = ?
                """,
                (rs, rowNum) -> new IdempotencyRecord(
                        rs.getObject("tenant_id", UUID.class).toString(),
                        rs.getString("idempotency_key"),
                        rs.getString("request_fingerprint"),
                        rs.getInt("response_status"),
                        rs.getString("response_body"),
                        rs.getTimestamp("created_at").toInstant()),
                UUID.fromString(tenantId),
                key);
        return found.stream().findFirst();
    }

    @Override
    public boolean saveIfAbsent(IdempotencyRecord record) {
        try {
            jdbc.update(
                    """
                    INSERT INTO idempotency_record (tenant_id, idempotency_key, request_fingerprint,
                                                    response_status, response_body, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.fromString(record.tenantId()),
                    record.key(),
                    record.requestFingerprint(),
                    record.responseStatus(),
                    record.responseBody(),
                    Timestamp.from(record.createdAt()));
            return true;
        } catch (DuplicateKeyException raced) {
            return false;
        }
    }

    @Override
    public int expireBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM idempotency_record WHERE created_at < ?", Timestamp.from(cutoff));
    }
}
