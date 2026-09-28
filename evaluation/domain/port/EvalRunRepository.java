package com.atlas.evaluation.domain.port;

import com.atlas.evaluation.domain.model.EvalRun;
import com.atlas.evaluation.domain.model.EvalRunId;
import java.util.Optional;

public interface EvalRunRepository {

    Optional<EvalRun> find(EvalRunId id);

    void save(EvalRun run);
}
