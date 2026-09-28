package com.atlas.evaluation.domain.port;

import com.atlas.evaluation.domain.model.Baseline;
import com.atlas.evaluation.domain.model.BaselineId;
import java.util.Optional;

public interface BaselineRepository {

    Optional<Baseline> find(BaselineId id);

    /**
     * Stores a baseline. Pinning the same id twice is refused by the adapter, not overwritten.
     *
     * <p>A baseline that can be replaced in place is a baseline that can be moved to wherever the
     * current build happens to sit, which is how a gate becomes a record of what the system does
     * rather than of what it must do.
     */
    void pin(Baseline baseline);
}
