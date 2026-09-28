package com.atlas.evaluation.adapter.in.web;

import com.atlas.contracts.v1.PublishRubricRequest;
import com.atlas.contracts.v1.PublishRubricResponse;
import com.atlas.contracts.v1.RecordEvalRunRequest;
import com.atlas.contracts.v1.RecordEvalRunResponse;
import com.atlas.evaluation.api.EvalRunSubmission;
import com.atlas.evaluation.api.EvaluationApi;
import com.atlas.evaluation.api.RubricDefinition;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter: the boundary the benchmark harness submits results through.
 *
 * <p>Deliberately the only way a run is admitted. A harness that gates itself grades its own
 * homework — the same process that produced the number decides whether the number is acceptable,
 * and under deadline pressure those two decisions move together.
 */
@RestController
@RequestMapping("/api/v1/evaluation")
class EvaluationController {

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser();

    private final EvaluationApi evaluation;

    EvaluationController(EvaluationApi evaluation) {
        this.evaluation = evaluation;
    }

    @PostMapping(value = "/rubrics", produces = MediaType.APPLICATION_JSON_VALUE)
    String publish(@RequestBody String body) throws InvalidProtocolBufferException {
        PublishRubricRequest.Builder request = PublishRubricRequest.newBuilder();
        PARSER.merge(body, request);

        int share = evaluation.publishRubric(new RubricDefinition(
                request.getRubricId(),
                request.getVersion(),
                request.getQuestion(),
                Instant.parse(request.getAsOf()),
                request.getCriteriaList().stream()
                        .map(c -> new RubricDefinition.CriterionDefinition(
                                c.getId(), c.getAxis(), c.getStatement(), c.getWeight()))
                        .toList()));

        return PRINTER.print(PublishRubricResponse.newBuilder()
                .setRubricId(request.getRubricId())
                .setVersion(request.getVersion())
                .setLoadBearingSharePercent(share)
                .build());
    }

    @PostMapping(value = "/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    String record(@RequestBody String body) throws InvalidProtocolBufferException {
        RecordEvalRunRequest.Builder request = RecordEvalRunRequest.newBuilder();
        PARSER.merge(body, request);

        List<String> regressions = evaluation.recordRun(new EvalRunSubmission(
                request.getRunId(),
                request.getBenchmark(),
                request.getRubricId(),
                request.getRubricVersion(),
                request.getBaselineId(),
                Instant.parse(request.getRanAt()),
                request.getSystemFingerprint(),
                request.getQuestions(),
                request.getScored(),
                request.getMetricsMap(),
                request.getCostPerQuestionMicros()));

        return PRINTER.print(
                RecordEvalRunResponse.newBuilder().addAllRegressions(regressions).build());
    }

    /**
     * An inadmissible submission is a 422, not a 400.
     *
     * <p>The payload was well-formed and the run is not acceptable — a draft rubric, a baseline
     * graded by a different version. Reporting it as a parse error sends someone looking at JSON;
     * what it actually means is that the experiment was not set up correctly.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    String inadmissible(IllegalStateException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidProtocolBufferException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(Exception e) {
        return e.getMessage();
    }
}
