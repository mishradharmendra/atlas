package com.atlas.knowledge.adapter.in.web;

import com.atlas.contracts.v1.CorroborateRequest;
import com.atlas.contracts.v1.CorroborateResponse;
import com.atlas.contracts.v1.ProposeEdgeRequest;
import com.atlas.contracts.v1.ProposeEdgeResponse;
import com.atlas.contracts.v1.ResolveEntityRequest;
import com.atlas.contracts.v1.ResolveEntityResponse;
import com.atlas.contracts.v1.TraverseRequest;
import com.atlas.contracts.v1.TraverseResponse;
import com.atlas.knowledge.api.CorroborationCommand;
import com.atlas.knowledge.api.KnowledgeApi;
import com.atlas.knowledge.api.ProposeEdgeCommand;
import com.atlas.knowledge.api.Resolution;
import com.atlas.knowledge.api.SourceQuality;
import com.atlas.knowledge.api.VerificationOutcome;
import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.BitemporalAsOf;
import com.atlas.shared.temporal.Validity;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter: the boundary the enrichment pipeline reaches the graph through.
 *
 * <p>Deliberately the only way an edge enters. The pipeline proposes and corroborates; it cannot
 * publish. An extraction service that could also make its own conclusions visible would, under
 * deadline pressure, start doing exactly that — and the withheld-by-default design would become a
 * convention rather than an invariant.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
class KnowledgeController {

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser();

    private final KnowledgeApi knowledge;
    private final com.atlas.knowledge.application.EntityRegistration registration;

    KnowledgeController(
            KnowledgeApi knowledge,
            com.atlas.knowledge.application.EntityRegistration registration) {
        this.knowledge = knowledge;
        this.registration = registration;
    }

    /**
     * Records an entity a question may name.
     *
     * <p>Without this the resolver had nothing to resolve against, and every mention abstained —
     * which reads identically to the resolver correctly refusing an ambiguous name. A question
     * naming an issuer was then retrieved with no entity filter at all, so "what did Microsoft
     * report" came back citing JPMorgan.
     */
    @PostMapping(value = "/entities", produces = MediaType.APPLICATION_JSON_VALUE)
    java.util.Map<String, String> registerEntity(
            @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<String> aliases = (java.util.List<String>)
                body.getOrDefault("aliases", java.util.List.of());
        String id = registration.register(
                String.valueOf(body.get("entityId")),
                com.atlas.knowledge.domain.model.EntityKind.valueOf(
                        String.valueOf(body.getOrDefault("kind", "COMPANY"))),
                String.valueOf(body.get("canonicalName")),
                aliases,
                String.valueOf(body.getOrDefault("source", "unknown")),
                body.get("knownFrom") == null
                        ? Instant.EPOCH
                        : Instant.parse(String.valueOf(body.get("knownFrom"))));
        return java.util.Map.of("entityId", id);
    }

    /**
     * The issuers this deployment can answer about.
     *
     * <p>Coverage is part of the answer. A workspace that cannot say what it holds makes the
     * asker find out by getting nothing back, which looks the same as a fault.
     */
    @org.springframework.web.bind.annotation.GetMapping(
            value = "/entities",
            produces = MediaType.APPLICATION_JSON_VALUE)
    java.util.List<java.util.Map<String, String>> coverage() {
        return registration.coverage().stream()
                .map(entity -> java.util.Map.of(
                        "entityId", entity.id().value(),
                        "canonicalName", entity.canonicalName()))
                .toList();
    }

    @PostMapping(value = "/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    String resolve(@RequestBody String body) throws InvalidProtocolBufferException {
        ResolveEntityRequest.Builder request = ResolveEntityRequest.newBuilder();
        PARSER.merge(body, request);

        Resolution resolution =
                knowledge.resolve(request.getMention(), AsOf.at(Instant.parse(request.getAsOf())));

        ResolveEntityResponse.Builder response = ResolveEntityResponse.newBuilder();
        switch (resolution) {
            case Resolution.Resolved resolved -> response
                    .setResolved(true)
                    .setEntityId(resolved.entityId())
                    .setCanonicalName(resolved.canonicalName())
                    .setConfidence(resolved.confidence());
            case Resolution.Abstained abstained -> response
                    .setResolved(false)
                    .setAbstentionReason(abstained.abstention().reason().name())
                    .setAbstentionDetail(abstained.abstention().detail())
                    .addAllCandidateEntityIds(abstained.candidates());
        }
        return PRINTER.print(response.build());
    }

    @PostMapping(value = "/edges", produces = MediaType.APPLICATION_JSON_VALUE)
    String propose(@RequestBody String body) throws InvalidProtocolBufferException {
        ProposeEdgeRequest.Builder request = ProposeEdgeRequest.newBuilder();
        PARSER.merge(body, request);

        // An empty valid_to is an open interval, not the epoch. Proto3 has no null for a scalar,
        // so the absence has to be given a meaning here rather than left to each caller.
        String validTo = request.getValidTo();
        Validity validity = validTo.isBlank()
                ? Validity.openFrom(Instant.parse(request.getValidFrom()))
                : Validity.between(Instant.parse(request.getValidFrom()), Instant.parse(validTo));

        String id = knowledge.propose(new ProposeEdgeCommand(
                request.getSubjectEntityId(),
                request.getRelationshipType(),
                request.getObjectEntityId(),
                validity,
                request.getExtractorModel(),
                request.getExtractorFamily(),
                request.getSourceId(),
                KnowledgeProtoMapper.citations(request.getEvidenceList())));

        return PRINTER.print(ProposeEdgeResponse.newBuilder()
                .setRelationshipId(id)
                .setStatus("WITHHELD")
                .build());
    }

    @PostMapping(value = "/edges/corroborations", produces = MediaType.APPLICATION_JSON_VALUE)
    String corroborate(@RequestBody String body) throws InvalidProtocolBufferException {
        CorroborateRequest.Builder request = CorroborateRequest.newBuilder();
        PARSER.merge(body, request);

        VerificationOutcome outcome = knowledge.corroborate(new CorroborationCommand(
                request.getRelationshipId(),
                request.getExtractorModel(),
                request.getExtractorFamily(),
                request.getAssertedRelationshipType(),
                request.getAssertedObjectEntityId(),
                request.getNote()));

        CorroborateResponse.Builder response = CorroborateResponse.newBuilder();
        switch (outcome) {
            case VerificationOutcome.Verified verified -> response
                    .setVerified(true)
                    .setRelationshipId(verified.relationshipId())
                    .setFirstExtractor(verified.firstExtractor())
                    .setSecondExtractor(verified.secondExtractor());
            case VerificationOutcome.Disputed disputed -> response
                    .setVerified(false)
                    .setRelationshipId(disputed.relationshipId())
                    .setReviewItemId(disputed.reviewItemId())
                    .setDisagreement(disputed.disagreement());
        }
        return PRINTER.print(response.build());
    }

    @PostMapping(value = "/traversals", produces = MediaType.APPLICATION_JSON_VALUE)
    String traverse(@RequestBody String body) throws InvalidProtocolBufferException {
        TraverseRequest.Builder request = TraverseRequest.newBuilder();
        PARSER.merge(body, request);

        if (request.getValidAt().isBlank() || request.getKnownAt().isBlank()) {
            throw new IllegalArgumentException(
                    "a traversal must carry both valid_at and known_at; an answer produced "
                            + "without recording the instants it was asked about cannot be replayed");
        }

        var paths = knowledge.pathsFrom(
                request.getEntityId(),
                request.getMaxHops(),
                new BitemporalAsOf(
                        AsOf.at(Instant.parse(request.getValidAt())),
                        AsOf.at(Instant.parse(request.getKnownAt()))));

        return PRINTER.print(TraverseResponse.newBuilder()
                .addAllPaths(paths.stream().map(KnowledgeProtoMapper::path).toList())
                .build());
    }

    /**
     * Per-source extraction quality.
     *
     * <p>Plain JSON rather than protobuf: this is an operational read model for humans and
     * dashboards, not a contract two planes have to agree on byte for byte. A rate is absent
     * rather than zero when the sample is too small to mean anything.
     */
    @GetMapping(value = "/sources/quality", produces = MediaType.APPLICATION_JSON_VALUE)
    List<Map<String, Object>> quality() {
        return knowledge.allSourceQuality().stream().map(KnowledgeController::report).toList();
    }

    private static Map<String, Object> report(SourceQuality quality) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", quality.sourceId());
        out.put("edgesProposed", quality.edgesProposed());
        out.put("disputesRaised", quality.disputesRaised());
        out.put("disputesResolved", quality.resolved());
        out.put("minSample", SourceQuality.MIN_SAMPLE);
        quality.disagreementRate().ifPresent(rate -> out.put("disagreementRate", rate));
        quality.refutedRate().ifPresent(rate -> out.put("refutedRate", rate));
        quality.undecidableRate().ifPresent(rate -> out.put("undecidableRate", rate));
        return out;
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(NoSuchElementException e) {
        return e.getMessage();
    }
    /**
     * A same-family corroboration is a 422, not a 400.
     *
     * <p>The payload parsed and the request is not admissible: the caller asked the platform to
     * treat two correlated extractors as independent. Reporting it as a parse error sends someone
     * to look at JSON, when what is wrong is the routing table.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    String inadmissible(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidProtocolBufferException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(Exception e) {
        return e.getMessage();
    }
}
