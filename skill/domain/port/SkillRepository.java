package com.atlas.skill.domain.port;

import com.atlas.skill.domain.model.Skill;
import com.atlas.skill.domain.model.SkillId;
import java.util.List;
import java.util.Optional;

/** Durable store for skills. */
public interface SkillRepository {

    /**
     * A specific version.
     *
     * <p>There is deliberately no {@code findLatest}. A run that resolved "the latest version"
     * at execution time could not be compared with a run from last week, because the procedure
     * silently differs — and the comparison would look valid. Callers name the version.
     */
    Optional<Skill> find(SkillId id, int version);

    List<Skill> versionsOf(SkillId id);

    void save(Skill skill);
}
