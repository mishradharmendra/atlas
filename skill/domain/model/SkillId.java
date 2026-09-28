package com.atlas.skill.domain.model;

/** Identity of a skill, stable across its versions. */
public record SkillId(String value) {

    public SkillId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("skill id must not be blank");
        }
    }

    public static SkillId of(String value) {
        return new SkillId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
