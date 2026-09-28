package com.atlas.skill.api;

/** Use cases for the skill catalogue. */
public interface SkillApi {

    /**
     * Authors and freezes a skill version.
     *
     * <p>Idempotent on an identical definition, and refuses an altered one at the same version —
     * a version number that means two different procedures makes every run recorded against it
     * unattributable.
     *
     * @return the skill's versioned identity, as {@code skill@vN}
     */
    String publish(PublishSkillCommand command);

    SkillView find(String skillId, int version);
}
