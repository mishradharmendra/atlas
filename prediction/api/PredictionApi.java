package com.atlas.prediction.api;

import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.Optional;

/** The prediction module's inbound port. */
public interface PredictionApi {

    /** Records a falsifiable claim. Refuses one that is not. */
    PredictionView record(NewPrediction command);

    /** Grades a due prediction. Refuses before its date, and refuses a second grading. */
    PredictionView resolve(String predictionId, String outcome, Instant observedAt, String note);

    Optional<PredictionView> find(String predictionId);

    /** What a source's claims have been worth, derived from graded predictions. */
    SourceWeightView weightOf(TenantId tenant, String sourceId);

    /**
     * Marks due-but-ungraded predictions overdue. Returns how many.
     *
     * <p>Run on a schedule so the count is visible. The alternative is that they stay OPEN, and a
     * forecast record assembled from graded predictions alone is a record of the ones somebody
     * chose to grade.
     */
    int sweepOverdue();
}
