package com.atlas.knowledge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA row for one name an entity went by over one window. */
@Entity
@Table(name = "knowledge_alias")
class AliasRow {

    @Id
    @Column(name = "alias_id", nullable = false, length = 128)
    String aliasId;

    @Column(name = "entity_id", nullable = false, length = 128)
    String entityId;

    @Column(name = "alias_value", nullable = false, length = 512)
    String aliasValue;

    @Column(name = "normalised_value", nullable = false, length = 512)
    String normalisedValue;

    @Column(name = "valid_from", nullable = false)
    Instant validFrom;

    @Column(name = "valid_to")
    Instant validTo;

    @Column(name = "asserted_by_source", nullable = false, length = 128)
    String assertedBySource;

    protected AliasRow() {
        // required by JPA
    }
}
