package com.atlas.knowledge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA row for a canonical entity. */
@Entity
@Table(name = "knowledge_entity")
class EntityRow {

    @Id
    @Column(name = "entity_id", nullable = false, length = 128)
    String entityId;

    @Column(name = "kind", nullable = false, length = 32)
    String kind;

    @Column(name = "canonical_name", nullable = false, length = 512)
    String canonicalName;

    protected EntityRow() {
        // required by JPA
    }
}
