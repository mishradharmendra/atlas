package com.atlas.prediction.adapter.in.web;

import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import com.atlas.prediction.api.NewPrediction;
import com.atlas.prediction.api.PredictionApi;
import com.atlas.prediction.api.PredictionView;
import com.atlas.prediction.api.SourceWeightView;
import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/predictions")
class PredictionController {

    private final PredictionApi predictions;

    PredictionController(PredictionApi predictions) {
        this.predictions = predictions;
    }

    /**
     * Records a prediction against the caller's own tenant.
     *
     * <p>The tenant is taken from the verified credential and is not a field on the request. A
     * forecast record is a scoreboard, and a scoreboard that accepts entries attributed to
     * somebody else is not one.
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PredictionView record(@RequestBody NewPredictionRequest body) {
        RequestTenant caller = TenantContext.require();
        return predictions.record(new NewPrediction(
                caller.tenantId(),
                body.claim(),
                body.resolutionCriterion(),
                body.confidence(),
                body.resolvesAt(),
                body.sourceIds(),
                body.artifactId()));
    }

    @PostMapping(value = "/{predictionId}/resolution", produces = MediaType.APPLICATION_JSON_VALUE)
    PredictionView resolve(
            @PathVariable String predictionId, @RequestBody ResolutionRequest body) {
        TenantContext.require();
        return predictions.resolve(
                predictionId, body.outcome(), body.observedAt(), body.note());
    }

    @GetMapping(value = "/{predictionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    PredictionView find(@PathVariable String predictionId) {
        TenantContext.require();
        return predictions
                .find(predictionId)
                .orElseThrow(() -> new IllegalArgumentException("no prediction " + predictionId));
    }

    @GetMapping(value = "/sources/{sourceId}/weight", produces = MediaType.APPLICATION_JSON_VALUE)
    SourceWeightView weight(@PathVariable String sourceId) {
        RequestTenant caller = TenantContext.require();
        return predictions.weightOf(TenantId.of(caller.tenantId()), sourceId);
    }

    @PostMapping(value = "/overdue/sweep", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Integer> sweep() {
        TenantContext.require();
        return Map.of("markedOverdue", predictions.sweepOverdue());
    }

    /**
     * An unfalsifiable or early-graded prediction is a 400, not a 500.
     *
     * <p>The caller asked for something the domain refuses, and the message says which rule. A
     * 500 would read as an outage and the author would retry the same claim.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> refused(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    record NewPredictionRequest(
            String claim,
            String resolutionCriterion,
            double confidence,
            Instant resolvesAt,
            List<String> sourceIds,
            String artifactId) {}

    record ResolutionRequest(String outcome, Instant observedAt, String note) {}
}
