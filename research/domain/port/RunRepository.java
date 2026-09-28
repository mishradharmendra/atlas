package com.atlas.research.domain.port;

import com.atlas.research.domain.model.Run;
import com.atlas.research.domain.model.RunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable store for runs and their traces. */
public interface RunRepository {

    Optional<Run> findById(RunId id);

    /**
     * Runs stopped at a ceiling and waiting for someone to decide.
     *
     * <p>A first-class query rather than a filter someone writes ad hoc. A suspended run costs
     * nothing and delivers nothing, so without somewhere to see them they accumulate silently and
     * the user concludes the product does not answer.
     */
    List<Run> suspended(int limit);

    /**
     * Runs whose executor stopped renewing its lease.
     *
     * <p>The query that turns an invisible failure into a visible one. Without it a crashed
     * executor leaves a run RUNNING for ever: no error is raised anywhere, the user waits, and
     * the budget stays reserved against work that stopped hours ago.
     */
    List<Run> orphaned(Instant now, int limit);

    void save(Run run);
}
