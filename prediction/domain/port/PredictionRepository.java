package com.atlas.prediction.domain.port;

import com.atlas.prediction.domain.model.Prediction;
import com.atlas.prediction.domain.model.PredictionId;
import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PredictionRepository {

    void save(Prediction prediction);

    Optional<Prediction> findById(PredictionId id);

    /**
     * Every prediction that rested on a source, whatever its status.
     *
     * <p>Status-blind because the weight calculation needs to see what it is excluding. A query
     * that returned only resolved predictions would make a source with fifty ungraded claims
     * indistinguishable from one with none.
     */
    List<Prediction> restingOn(TenantId tenant, String sourceId);

    /** Open predictions whose date has passed. What the overdue sweep works through. */
    List<Prediction> dueBefore(Instant now, int limit);
}
