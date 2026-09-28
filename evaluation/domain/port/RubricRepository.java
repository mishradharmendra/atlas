package com.atlas.evaluation.domain.port;

import com.atlas.evaluation.domain.model.Rubric;
import com.atlas.evaluation.domain.model.RubricId;
import java.util.Optional;

public interface RubricRepository {

    Optional<Rubric> find(RubricId id, int version);

    /** The highest frozen version, which is what a run must be graded by. */
    Optional<Rubric> findLatestFrozen(RubricId id);

    void save(Rubric rubric);
}
