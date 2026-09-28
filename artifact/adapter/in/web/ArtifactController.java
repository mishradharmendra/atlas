package com.atlas.artifact.adapter.in.web;

import com.atlas.artifact.api.ArtifactApi;
import com.atlas.artifact.api.ArtifactDetailView;
import com.atlas.artifact.api.ArtifactView;
import com.atlas.artifact.api.ExportNotPermittedException;
import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import com.atlas.platform.tenant.TenantMismatchException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * The deliverable's HTTP surface: what a workspace grid reads and what a spreadsheet writes back.
 *
 * <p>Every read is checked against the caller's tenant. An artifact is the most sensitive object
 * in the platform — it is the document a client is sent — and an id is guessable in a way a
 * tenant boundary is not.
 */
@RestController
@RequestMapping("/api/v1/artifacts")
class ArtifactController {

    private final ArtifactApi artifacts;
    private final com.atlas.artifact.application.ArtifactExport export;

    ArtifactController(
            ArtifactApi artifacts, com.atlas.artifact.application.ArtifactExport export) {
        this.artifacts = artifacts;
        this.export = export;
    }

    /**
     * The deliverable as a spreadsheet.
     *
     * <p>The alternative to this endpoint is not "no export", it is retyping — and a retyped
     * figure arrives at the client with its citation stripped off.
     */
    @GetMapping(value = "/{artifactId}/export", produces = "text/csv")
    ResponseEntity<String> exportCsv(@PathVariable String artifactId) {
        RequestTenant caller = TenantContext.require();
        ArtifactDetailView artifact = artifacts
                .detail(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        requireOwn(caller, artifact.tenantId(), artifact);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"atlas-%s.csv\"".formatted(artifactId))
                .body(export.csv(artifactId));
    }

    @GetMapping(value = "/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ArtifactDetailView detail(@PathVariable String artifactId) {
        RequestTenant caller = TenantContext.require();
        ArtifactDetailView artifact = artifacts
                .detail(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        return requireOwn(caller, artifact.tenantId(), artifact);
    }

    /**
     * Deliverables for a run, or the caller's own most recent ones when no run is named.
     *
     * <p>Without the second case a workspace can only open an artifact whose id somebody
     * already knew, which means the product's front door is a UUID typed from elsewhere.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<ArtifactView> list(
            @RequestParam(required = false) String runId,
            @RequestParam(defaultValue = "20") int limit) {
        RequestTenant caller = TenantContext.require();
        if (runId == null || runId.isBlank()) {
            // Scoped by the verified tenant, never by a parameter. A tenant id in the query
            // string would make another tenant's shelf readable by typing its uuid.
            return artifacts.recentFor(caller.tenantId(), Math.clamp(limit, 1, 100));
        }
        // Filtered rather than refused: a run id that belongs to someone else should look like a
        // run with no deliverables, not like a permission error confirming it exists.
        return artifacts.forRun(runId).stream()
                .filter(artifact -> caller.mayActOn(artifact.tenantId()))
                .toList();
    }

    /** The spreadsheet write-back. */
    @PostMapping(
            value = "/{artifactId}/cells/{cellRef}/override",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ArtifactDetailView override(
            @PathVariable String artifactId,
            @PathVariable String cellRef,
            @RequestBody OverrideRequest body) {

        RequestTenant caller = TenantContext.require();
        ArtifactDetailView before = artifacts
                .detail(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        requireOwn(caller, before.tenantId(), before);

        // Attributed to the verified principal, not to a name in the body. An audit trail whose
        // author is supplied by the caller records who they said they were.
        return artifacts.override(
                artifactId, cellRef, body.value(), caller.principalId(), body.rationale());
    }

    @PostMapping(value = "/{artifactId}/issuance", produces = MediaType.APPLICATION_JSON_VALUE)
    ArtifactView issue(@PathVariable String artifactId) {
        RequestTenant caller = TenantContext.require();
        ArtifactView artifact = artifacts
                .find(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        requireOwn(caller, artifact.tenantId(), artifact);
        return artifacts.issue(artifactId, caller.principalId());
    }

    private static <T> T requireOwn(RequestTenant caller, String owner, T value) {
        if (!caller.mayActOn(owner)) {
            throw new TenantMismatchException(caller.tenantId(), owner);
        }
        return value;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> refused(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    /** 403, not 400: the request is correct and the licence is what says no. */
    @ExceptionHandler(ExportNotPermittedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> notLicensed(ExportNotPermittedException e) {
        return Map.of("message", e.getMessage());
    }

    record OverrideRequest(String value, String rationale) {}
}
