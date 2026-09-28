package com.atlas.knowledge.adapter.out.persistence;

import com.atlas.knowledge.domain.model.Alias;
import com.atlas.knowledge.domain.model.CanonicalEntity;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.EntityKind;
import com.atlas.knowledge.domain.port.EntityRepository;
import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.Validity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

interface EntityJpaRepository extends JpaRepository<EntityRow, String> {}

interface AliasJpaRepository extends JpaRepository<AliasRow, String> {

    List<AliasRow> findByEntityId(String entityId);

    void deleteByEntityId(String entityId);

    /**
     * Entities answering to a name at an instant.
     *
     * <p>The window test is half-open in SQL for the same reason it is in {@link Validity}: a
     * name handed over on the 31st must not match both the old holder and the new one.
     */
    @Query("""
            select a.entityId from AliasRow a
            where a.normalisedValue = :name
              and a.validFrom <= :at
              and (a.validTo is null or a.validTo > :at)
            """)
    List<String> findEntityIdsByNameAt(@Param("name") String name, @Param("at") Instant at);

    @Query("""
            select e.entityId from EntityRow e
            where lower(e.canonicalName) = :name
            """)
    List<String> findEntityIdsByCanonicalName(@Param("name") String name);
}

/**
 * Driven adapter for entities and their aliases.
 *
 * <p>Aliases are replaced wholesale on save rather than diffed. The set is small, bounded by how
 * many names one company has ever traded under, and a diff would have to reproduce the overlap
 * rule to decide what is an update and what is a new window — a second implementation of an
 * invariant that already exists in two places.
 */
@Repository
class JpaEntityRepository implements EntityRepository {

    private final EntityJpaRepository entities;
    private final AliasJpaRepository aliases;

    JpaEntityRepository(EntityJpaRepository entities, AliasJpaRepository aliases) {
        this.entities = entities;
        this.aliases = aliases;
    }

    @Override
    public Optional<CanonicalEntity> findById(EntityId id) {
        return entities.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<CanonicalEntity> findByName(String name, AsOf at) {
        String normalised = Alias.normalise(name);

        // Canonical names are matched as well as aliases, so an entity is always findable under
        // the name it is filed under even before anyone records an alias for it.
        List<String> ids = java.util.stream.Stream.concat(
                        aliases.findEntityIdsByNameAt(normalised, at.instant()).stream(),
                        aliases.findEntityIdsByCanonicalName(normalised).stream())
                .distinct()
                .toList();

        return ids.stream()
                .map(entities::findById)
                .flatMap(Optional::stream)
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<CanonicalEntity> all() {
        return entities.findAll(org.springframework.data.domain.Sort.by("canonicalName")).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void save(CanonicalEntity entity) {
        EntityRow row = entities.findById(entity.id().value()).orElseGet(EntityRow::new);
        row.entityId = entity.id().value();
        row.kind = entity.kind().name();
        row.canonicalName = entity.canonicalName();
        entities.save(row);

        aliases.deleteByEntityId(entity.id().value());
        // Flushed before the rewrite, because Hibernate orders inserts ahead of deletes within
        // a flush. The alias ids are derived from the entity id and an ordinal, so a re-save
        // writes the same primary keys the delete is about to remove: without this the second
        // save of an entity leaves it with no aliases at all, and resolution starts abstaining
        // with NO_EVIDENCE_EXISTS for a name it was told about twice.
        aliases.flush();

        int ordinal = 0;
        for (Alias alias : entity.aliases()) {
            AliasRow aliasRow = new AliasRow();
            aliasRow.aliasId = "%s#%d".formatted(entity.id().value(), ordinal++);
            aliasRow.entityId = entity.id().value();
            aliasRow.aliasValue = alias.value();
            aliasRow.normalisedValue = alias.normalised();
            aliasRow.validFrom = alias.validity().from();
            aliasRow.validTo = alias.validity().to();
            aliasRow.assertedBySource = alias.assertedBySource();
            aliases.save(aliasRow);
        }
    }

    private CanonicalEntity toDomain(EntityRow row) {
        CanonicalEntity entity = new CanonicalEntity(
                EntityId.of(row.entityId), EntityKind.valueOf(row.kind), row.canonicalName);

        entity.rehydrate(aliases.findByEntityId(row.entityId).stream()
                .map(aliasRow -> new Alias(
                        aliasRow.aliasValue,
                        new Validity(aliasRow.validFrom, aliasRow.validTo),
                        aliasRow.assertedBySource))
                .toList());
        return entity;
    }
}
