package com.atlas.skill.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA row for one version of a skill. */
@Entity
@Table(name = "skill")
@IdClass(SkillRow.Key.class)
class SkillRow {

    @Id
    @Column(name = "skill_id", nullable = false, length = 128)
    String skillId;

    @Id
    @Column(name = "version", nullable = false)
    Integer version;

    @Column(name = "name", nullable = false, length = 256)
    String name;

    @Column(name = "intent", nullable = false, length = 1024)
    String intent;

    @Column(name = "steps", nullable = false, columnDefinition = "text")
    String steps;

    @Column(name = "frozen", nullable = false)
    boolean frozen;

    @Column(name = "frozen_at")
    Instant frozenAt;

    protected SkillRow() {
        // required by JPA
    }

    static class Key implements Serializable {
        String skillId;
        Integer version;

        Key() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(skillId, key.skillId)
                    && Objects.equals(version, key.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(skillId, version);
        }
    }
}
