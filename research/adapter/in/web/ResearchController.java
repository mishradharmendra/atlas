package com.atlas.research.adapter.in.web;

import com.atlas.artifact.api.NewCell;
import com.atlas.research.api.MandatoryStepAbstainedResponse;
import com.atlas.research.api.RecordStepCommand;
import com.atlas.research.api.ReplayReport;
import com.atlas.research.api.ResearchApi;
import com.atlas.research.api.RunView;
import com.atlas.research.api.TraceEntryView;
import com.atlas.research.api.GuardrailNotSatisfiedException;
import com.atlas.research.api.StartRunCommand;
import com.atlas.research.domain.model.MandatoryStepAbstainedException;
import com.atlas.research.domain.model.RunAlreadyClaimedException;
import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import com.atlas.platform.tenant.TenantMismatchException;
import com.atlas.platform.tenant.UnauthenticatedRequestException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

/**
 * Driving adapter: the boundary a step executor drives a run through.
 *
 * <p>The executor lives in the compute plane. It calls models, retrieves, and reports what
 * happened; it does not decide whether the run may finish or whether the result may be shown.
 * Those need a transaction and an audit trail, so they are answered here.
 */
@RestController
@RequestMapping("/api/v1/research")
class ResearchController {

    private final ResearchApi research;

    ResearchController(ResearchApi research) {
        this.research = research;
    }

    @PostMapping(value = "/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> start(@RequestBody StartRunCommand command) {
        // The body still carries a tenant id because the command is the domain's shape, but it
        // is checked against the verified identity rather than trusted. Silently overriding it
        // would make a cross-tenant attempt succeed against the attacker's own data and leave
        // no trace.
        RequestTenant caller = TenantContext.require();
        if (!caller.mayActOn(command.tenantId().toString())) {
            throw new TenantMismatchException(caller.tenantId(), command.tenantId().toString());
        }
        // The denied labels come from the verified credential, never from the body. A caller
        // that could assert its own restrictions could satisfy any guardrail by claiming to.
        return Map.of(
                "runId",
                research.start(new StartRunCommand(
                        command.tenantId(),
                        command.principalId(),
                        command.question(),
                        command.skillId(),
                        command.skillVersion(),
                        command.model(),
                        command.promptVersion(),
                        command.indexVersion(),
                        command.retrievalParams(),
                        command.maxTokens(),
                        command.maxWallMillis(),
                        command.maxSpendMicros(),
                        caller.deniedLabels())));
    }

    /**
     * A guardrail the platform could not hold.
     *
     * <p>403 rather than 400: the request is well-formed and this credential may not make it.
     * The remedy is a different credential, not a corrected payload.
     */
    @ExceptionHandler(GuardrailNotSatisfiedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String guardrail(GuardrailNotSatisfiedException e) {
        return e.getMessage();
    }

    @PostMapping(value = "/runs/{runId}/claim", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView claim(@PathVariable String runId, @RequestParam String executorId) {
        return research.claim(runId, executorId);
    }

    @PostMapping(value = "/runs/{runId}/heartbeat", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView heartbeat(@PathVariable String runId, @RequestParam String executorId) {
        return research.heartbeat(runId, executorId);
    }

    @PostMapping(value = "/runs/{runId}/steps", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> recordStep(
            @PathVariable String runId, @RequestBody RecordStepCommand body) {
        // The path is authoritative: a body that disagreed with it would let an executor write
        // into a run it was not given.
        String status = research.recordStep(new RecordStepCommand(
                runId,
                body.executorId(),
                body.stepId(),
                body.attempt(),
                body.action(),
                body.observation(),
                body.outcome(),
                body.abstentionReason(),
                body.abstentionDetail(),
                body.abstentionCoverage(),
                body.tookMillis(),
                body.tokens(),
                body.costMicros()));
        return Map.of("status", status);
    }

    @PostMapping(value = "/runs/{runId}/completion", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView complete(@PathVariable String runId) {
        return research.complete(runId);
    }

    @PostMapping(value = "/runs/{runId}/artefact", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView emit(
            @PathVariable String runId,
            @RequestParam String title,
            @RequestBody List<NewCell> cells) {
        return research.emitArtefact(runId, title, cells);
    }

    @PostMapping(value = "/runs/{runId}/resumption", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView resume(
            @PathVariable String runId,
            @RequestParam long maxTokens,
            @RequestParam long maxWallMillis,
            @RequestParam long maxSpendMicros) {
        return research.resume(runId, maxTokens, maxWallMillis, maxSpendMicros);
    }

    @PostMapping(value = "/runs/{runId}/replay", produces = MediaType.APPLICATION_JSON_VALUE)
    ReplayReport replay(
            @PathVariable String runId,
            @RequestParam String model,
            @RequestParam String indexVersion,
            @RequestBody List<String> observedOutcomes) {
        return research.replay(runId, model, indexVersion, observedOutcomes);
    }

    @PostMapping(value = "/runs/orphans/sweep", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Integer> reap() {
        return Map.of("abandoned", research.reapOrphanedRuns());
    }

    @GetMapping(value = "/runs/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    RunView find(@PathVariable String runId) {
        return guarded(research.find(runId));
    }

    /**
     * Refuses a run belonging to another tenant.
     *
     * <p>Checked on the way out rather than pushed into the query, because a query filtered by
     * tenant returns "not found" for another tenant's run — which is indistinguishable from a
     * run that never existed, and hides the attempt from anyone looking for it.
     */
    private RunView guarded(RunView view) {
        RequestTenant caller = TenantContext.require();
        if (!caller.mayActOn(view.tenantId())) {
            throw new TenantMismatchException(caller.tenantId(), view.tenantId());
        }
        return view;
    }

    @GetMapping(value = "/runs/{runId}/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    List<TraceEntryView> trace(@PathVariable String runId) {
        RequestTenant caller = TenantContext.require();
        RunView run = research.find(runId);
        if (!caller.mayActOn(run.tenantId())) {
            throw new TenantMismatchException(caller.tenantId(), run.tenantId());
        }
        return research.trace(runId);
    }

    @GetMapping(value = "/runs/suspended", produces = MediaType.APPLICATION_JSON_VALUE)
    List<RunView> suspended(@RequestParam(defaultValue = "50") int limit) {
        return research.suspended(limit);
    }

    /**
     * A blocked artefact is a 422 carrying the abstentions, not a 500.
     *
     * <p>The refusal is the product working. "I could not establish this, and here is what I
     * could not establish" is a correct final answer, and turning it into a server error would
     * make the one honest outcome look like an outage.
     */
    @ExceptionHandler(MandatoryStepAbstainedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    MandatoryStepAbstainedResponse blocked(MandatoryStepAbstainedException e) {
        return new MandatoryStepAbstainedResponse(
                e.getMessage(),
                e.blockingSteps(),
                e.abstentions().stream()
                        .map(a -> new MandatoryStepAbstainedResponse.Reason(
                                a.stepId(), a.reason().name(), a.detail(), a.isGroundTruth()))
                        .toList());
    }

    /**
     * A run held by another executor is a 409 carrying the holder.
     *
     * <p>The caller's next move depends on who holds it and until when: a lease expiring shortly
     * means wait, one held by a dead executor means the reaper will release it. A bare conflict
     * makes both look like a reason to retry in a loop.
     */
    @ExceptionHandler(RunAlreadyClaimedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> claimed(RunAlreadyClaimedException e) {
        return Map.of(
                "message", e.getMessage(),
                "heldBy", e.heldBy(),
                "expiresAt", String.valueOf(e.expiresAt()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String conflict(IllegalStateException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
