package com.atlas.skill.api;

import java.util.List;

/** A skill as the rest of the platform sees it. */
public record SkillView(
        String skillId,
        int version,
        String versionedId,
        String name,
        String intent,
        boolean frozen,
        List<String> stepIds,
        List<String> mandatoryStepIds,
        java.util.Set<String> guardrails) {}
