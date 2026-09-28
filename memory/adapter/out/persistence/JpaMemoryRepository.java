package com.atlas.memory.adapter.out.persistence;

import com.atlas.memory.domain.model.MemoryEntry;
import com.atlas.memory.domain.model.MemoryKind;
import com.atlas.memory.domain.port.MemoryRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Entity
@Table(name = "agent_memory")
class MemoryRow {

    @Id
    @Column(name = "entry_id", nullable = false, length = 128)
    String entryId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    String tenantId;

    @Column(name = "kind", nullable = false, length = 32)
    String kind;

    @Column(name = "text", nullable = false)
    String text;

    @Column(name = "written_by_run", nullable = false, length = 128)
    String writtenByRun;

    @Column(name = "written_at", nullable = false)
    Instant writtenAt;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Column(name = "withdrawn", nullable = false)
    boolean withdrawn;

    @Column(name = "withdrawn_reason", length = 1024)
    String withdrawnReason;

    protected MemoryRow() {
        // required by JPA
    }
}

class ChildKey implements Serializable {
    String entryId;
    String value;

    @Override
    public boolean equals(Object other) {
        return other instanceof ChildKey key
                && Objects.equals(entryId, key.entryId)
                && Objects.equals(value, key.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, value);
    }
}

@Entity
@Table(name = "agent_memory_source")
@IdClass(ChildKey.class)
class MemorySourceRow {

    @Id
    @Column(name = "entry_id", nullable = false, length = 128)
    String entryId;

    @Id
    @Column(name = "doc_id", nullable = false, length = 128)
    String value;

    protected MemorySourceRow() {
        // required by JPA
    }
}

@Entity
@Table(name = "agent_memory_tag")
@IdClass(ChildKey.class)
class MemoryTagRow {

    @Id
    @Column(name = "entry_id", nullable = false, length = 128)
    String entryId;

    @Id
    @Column(name = "tag", nullable = false, length = 128)
    String value;

    protected MemoryTagRow() {
        // required by JPA
    }
}

interface MemoryJpaRepository extends JpaRepository<MemoryRow, String> {

    /**
     * Live for a tenant at an instant. Withdrawal and expiry are filtered in SQL because they
     * are the two conditions no caller may override and both are indexed.
     */
    @Query("""
            select m from MemoryRow m
            where m.tenantId = :tenant
              and m.withdrawn = false
              and (m.expiresAt is null or m.expiresAt > :at)
            order by m.writtenAt desc
            """)
    List<MemoryRow> liveFor(
            @Param("tenant") String tenantId, @Param("at") Instant at, Limit limit);
}

interface MemorySourceJpaRepository extends JpaRepository<MemorySourceRow, ChildKey> {

    List<MemorySourceRow> findByEntryId(String entryId);

    List<MemorySourceRow> findByValueIn(Set<String> docIds);

    void deleteByEntryId(String entryId);
}

interface MemoryTagJpaRepository extends JpaRepository<MemoryTagRow, ChildKey> {

    List<MemoryTagRow> findByEntryId(String entryId);

    void deleteByEntryId(String entryId);
}

@Repository
class JpaMemoryRepository implements MemoryRepository {

    private final MemoryJpaRepository rows;
    private final MemorySourceJpaRepository sources;
    private final MemoryTagJpaRepository tags;

    JpaMemoryRepository(
            MemoryJpaRepository rows,
            MemorySourceJpaRepository sources,
            MemoryTagJpaRepository tags) {
        this.rows = rows;
        this.sources = sources;
        this.tags = tags;
    }

    @Override
    @Transactional
    public void save(MemoryEntry entry) {
        MemoryRow row = rows.findById(entry.entryId()).orElseGet(MemoryRow::new);
        row.entryId = entry.entryId();
        row.tenantId = entry.tenantId();
        row.kind = entry.kind().name();
        row.text = entry.text();
        row.writtenByRun = entry.writtenByRun();
        row.writtenAt = entry.writtenAt();
        row.expiresAt = entry.expiresAt();
        row.withdrawn = entry.isWithdrawn();
        row.withdrawnReason = entry.withdrawnReason();
        rows.save(row);

        // Replaced wholesale, and flushed before the rewrite: Hibernate orders inserts ahead of
        // deletes within a flush, and the child keys are the same on a re-save. Without the
        // flush the second save of an entry leaves it with no sources, which silently makes it
        // un-forgettable by the retraction cascade.
        sources.deleteByEntryId(entry.entryId());
        tags.deleteByEntryId(entry.entryId());
        sources.flush();
        tags.flush();

        for (String docId : entry.sourceDocIds()) {
            MemorySourceRow source = new MemorySourceRow();
            source.entryId = entry.entryId();
            source.value = docId;
            sources.save(source);
        }
        for (String tag : entry.entitlementTags()) {
            MemoryTagRow tagRow = new MemoryTagRow();
            tagRow.entryId = entry.entryId();
            tagRow.value = tag;
            tags.save(tagRow);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryEntry> liveFor(String tenantId, Instant at, int limit) {
        return rows.liveFor(tenantId, at, Limit.of(limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryEntry> citingAnyOf(Set<String> docIds) {
        return sources.findByValueIn(docIds).stream()
                .map(source -> source.entryId)
                .distinct()
                .map(rows::findById)
                .flatMap(java.util.Optional::stream)
                .map(this::toDomain)
                .toList();
    }

    private MemoryEntry toDomain(MemoryRow row) {
        Set<String> docIds = new LinkedHashSet<>(
                sources.findByEntryId(row.entryId).stream().map(s -> s.value).toList());
        Set<String> entryTags = new LinkedHashSet<>(
                tags.findByEntryId(row.entryId).stream().map(t -> t.value).toList());

        MemoryEntry entry = new MemoryEntry(
                row.entryId,
                row.tenantId,
                MemoryKind.valueOf(row.kind),
                row.text,
                row.writtenByRun,
                row.writtenAt,
                docIds,
                entryTags,
                row.expiresAt);
        entry.rehydrateWithdrawal(row.withdrawn, row.withdrawnReason);
        return entry;
    }
}
