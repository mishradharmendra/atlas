package com.atlas.prediction.application;

import com.atlas.prediction.api.NewPrediction;
import com.atlas.prediction.api.PredictionApi;
import com.atlas.prediction.api.PredictionView;
import com.atlas.prediction.api.SourceWeightView;
import com.atlas.prediction.domain.model.Outcome;
import com.atlas.prediction.domain.model.Prediction;
import com.atlas.prediction.domain.model.PredictionId;
import com.atlas.prediction.domain.model.SourceWeight;
import com.atlas.prediction.domain.port.PredictionRepository;
import com.atlas.shared.identity.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the prediction feedback loop. */
@Service
class PredictionService implements PredictionApi {

    /** How many overdue predictions one sweep will mark. Bounded so a backlog cannot hold a lock. */
    private static final int SWEEP_BATCH = 500;

    private final PredictionRepository predictions;
    private final Clock clock;

    PredictionService(PredictionRepository predictions, Clock clock) {
        this.predictions = predictions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PredictionView record(NewPrediction command) {
        Prediction prediction = new Prediction(
                PredictionId.of(UUID.randomUUID().toString()),
                TenantId.of(command.tenantId()),
                command.claim(),
                command.resolutionCriterion(),
                command.confidence(),
                clock.instant(),
                command.resolvesAt(),
                command.sourceIds(),
                command.artifactId());
        predictions.save(prediction);
        return view(prediction);
    }

    @Override
    @Transactional
    public PredictionView resolve(
            String predictionId, String outcome, Instant observedAt, String note) {
        Prediction prediction = predictions
                .findById(PredictionId.of(predictionId))
                .orElseThrow(() -> new IllegalArgumentException("no prediction " + predictionId));
        prediction.resolve(Outcome.valueOf(outcome), observedAt, note);
        predictions.save(prediction);
        return view(prediction);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PredictionView> find(String predictionId) {
        return predictions.findById(PredictionId.of(predictionId)).map(PredictionService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public SourceWeightView weightOf(TenantId tenant, String sourceId) {
        List<Prediction> restingOnIt = predictions.restingOn(tenant, sourceId);
        SourceWeight weight = SourceWeight.derivedFrom(sourceId, restingOnIt);
        return new SourceWeightView(
                weight.sourceId(),
                weight.scored(),
                weight.unresolvable(),
                boxed(weight.meanBrier()),
                boxed(weight.unresolvableRate()));
    }

    @Override
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public int sweepOverdue() {
        Instant now = clock.instant();
        List<Prediction> due = predictions.dueBefore(now, SWEEP_BATCH);
        for (Prediction prediction : due) {
            prediction.markOverdue(now);
            predictions.save(prediction);
        }
        return due.size();
    }

    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private static PredictionView view(Prediction prediction) {
        return new PredictionView(
                prediction.id().value(),
                prediction.tenant().value().toString(),
                prediction.claim(),
                prediction.resolutionCriterion(),
                prediction.confidence(),
                prediction.madeAt(),
                prediction.resolvesAt(),
                prediction.sourceIds(),
                prediction.artifactId(),
                prediction.status().name(),
                prediction.outcome() == null ? null : prediction.outcome().name(),
                prediction.resolvedAt(),
                prediction.resolutionNote(),
                boxed(prediction.brierScore()));
    }
}
