package com.atlas.entitlement.adapter.in.web;

import com.atlas.contracts.v1.IssueSnapshotRequest;
import com.atlas.contracts.v1.IssueSnapshotResponse;
import com.atlas.contracts.v1.RecordGrantRequest;
import com.atlas.contracts.v1.RecordGrantResponse;
import com.atlas.entitlement.api.EntitlementApi;
import com.atlas.entitlement.application.RecordEntitlementGrant;
import com.atlas.entitlement.domain.model.GrantId;
import com.atlas.identity.IdentityNotEstablishedException;
import com.atlas.identity.IdentityTokenVerifier;
import com.atlas.identity.VerifiedPrincipal;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter: the boundary the Python compute plane calls to obtain a snapshot.
 *
 * <p>Speaks canonical proto3 JSON rather than a Jackson serialisation of a Java record. The
 * difference is not cosmetic. A record's JSON shape is whatever its field names happen to be, so
 * renaming one is a source-compatible change that silently breaks the other plane at runtime.
 * Going through {@link JsonFormat} makes the wire shape a property of
 * {@code contracts/proto/atlas/v1/entitlement.proto}, which has a breaking-change gate over it.
 *
 * <p>JSON rather than gRPC for now: the contract is the valuable part and the transport is not.
 * gRPC transport is tracked as G1.7.
 */
@RestController
@RequestMapping("/api/v1/entitlement")
class EntitlementController {

    private static final Logger log = LoggerFactory.getLogger(EntitlementController.class);

    /** Named for what it is, and deliberately not {@code Authorization}. */
    static final String ADMIN_HEADER = "X-Atlas-Admin-Key";

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private final EntitlementApi entitlement;
    private final IdentityTokenVerifier identity;
    private final RecordEntitlementGrant grants;
    private final Clock clock;

    /**
     * The administrative credential for recording grants.
     *
     * <p>Deliberately not the entitlement snapshot. Every authenticated caller holds one of
     * those, and a caller who can grant is a caller who can grant itself everything, so reusing
     * it would make the whole entitlement model self-serve. Unset means grant administration is
     * disabled and the endpoint refuses, which is the right default for a capability whose
     * misuse is silent.
     */
    private final String adminKey;

    EntitlementController(
            EntitlementApi entitlement,
            IdentityTokenVerifier identity,
            RecordEntitlementGrant grants,
            Clock clock,
            @Value("${atlas.entitlement.admin-key:}") String adminKey) {
        this.entitlement = entitlement;
        this.identity = identity;
        this.grants = grants;
        this.clock = clock;
        this.adminKey = adminKey;
    }

    /**
     * Exchanges a verified OIDC token for an entitlement snapshot.
     *
     * <p>This is the platform's trust boundary, and until an identity provider was wired in it
     * was also its largest hole: tenant and principal were read from the request body, so any
     * caller could name any tenant and be handed a correctly signed credential for it. Every
     * check downstream was faithfully enforcing a claim anyone could make.
     *
     * <p>With a provider configured the body no longer decides who you are. It may still carry an
     * as-of, because "what could this person see last Tuesday" is a real question and not an
     * identity claim.
     */
    @PostMapping(value = "/snapshots", produces = MediaType.APPLICATION_JSON_VALUE)
    String issue(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InvalidProtocolBufferException {

        IssueSnapshotRequest.Builder request = IssueSnapshotRequest.newBuilder();
        PARSER.merge(body, request);

        // Absent as-of means "now", never "latest grants regardless of as-of" -- that would let
        // a point-in-time question see permissions that did not exist at the point in time.
        Instant asOf = EntitlementProtoMapper.asOf(request.getAsOf());

        TenantId tenant;
        PrincipalId principal;
        if (identity.isConfigured()) {
            VerifiedPrincipal caller = identity.verify(bearer(authorization));
            tenant = TenantId.of(caller.tenantId());
            principal = PrincipalId.of(caller.principalId());
        } else {
            // No provider configured: local development and the test suite. Logged every time,
            // because the whole point of the warning is that this mode is indistinguishable from
            // a working one right up until somebody deploys it.
            log.warn(
                    "issuing a snapshot from an unverified request: no identity provider is "
                            + "configured. Set atlas.identity.required=true outside development.");
            tenant = TenantId.of(request.getTenant().getValue());
            principal = PrincipalId.of(request.getPrincipal().getValue());
        }

        IssueSnapshotResponse response = EntitlementProtoMapper.toProto(
                entitlement.issue(tenant, principal, AsOf.at(asOf == null ? clock.instant() : asOf)));

        return PRINTER.print(response);
    }

    /**
     * Records a durable grant. Requires the administrative key, never a snapshot.
     *
     * <p>Grants are the commercial core: they say which tenant's contract covers which
     * publisher, for how long. They arrive from contract negotiation, not from a user session,
     * and nothing a user holds should be sufficient to create one.
     */
    @PostMapping(value = "/grants", produces = MediaType.APPLICATION_JSON_VALUE)
    String recordGrant(
            @RequestBody String body,
            @RequestHeader(value = ADMIN_HEADER, required = false) String presented)
            throws InvalidProtocolBufferException {

        requireAdmin(presented);

        RecordGrantRequest.Builder builder = RecordGrantRequest.newBuilder();
        PARSER.merge(body, builder);
        RecordGrantRequest request = builder.build();

        GrantId id = grants.record(
                TenantId.of(request.getTenant().getValue()),
                request.getPrincipal().getValue().isEmpty()
                        ? null
                        : PrincipalId.of(request.getPrincipal().getValue()),
                EntitlementProtoMapper.dimension(request.getDimension()),
                request.getValue(),
                EntitlementProtoMapper.effect(request.getEffect()),
                instantOrNull(request.getEffectiveFrom()),
                instantOrNull(request.getEffectiveTo()),
                request.getContractId());

        return PRINTER.print(
                RecordGrantResponse.newBuilder().setGrantId(id.value().toString()).build());
    }

    private void requireAdmin(String presented) {
        if (adminKey.isBlank()) {
            throw new IdentityNotEstablishedException(
                    "grant administration is disabled: set atlas.entitlement.admin-key");
        }
        // Constant-time: a timing oracle on this key is a path to granting yourself anything.
        if (presented == null
                || !MessageDigest.isEqual(
                        presented.getBytes(StandardCharsets.UTF_8),
                        adminKey.getBytes(StandardCharsets.UTF_8))) {
            throw new IdentityNotEstablishedException(
                    "recording a grant requires the administrative key in " + ADMIN_HEADER);
        }
    }

    private static Instant instantOrNull(String value) {
        return value.isEmpty() ? null : Instant.parse(value);
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IdentityNotEstablishedException(
                    "a bearer token is required to obtain an entitlement snapshot");
        }
        return authorization.substring(7).trim();
    }

    @ExceptionHandler(IdentityNotEstablishedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    String unauthenticated(IdentityNotEstablishedException e) {
        return e.getMessage();
    }

    /**
     * A refused issuance is a client error, not a server fault.
     *
     * <p>Mapped explicitly so it cannot surface as a 500 that an on-call engineer investigates as
     * an outage. A future as-of or a malformed identifier is the caller's mistake and should read
     * that way.
     */
    @ExceptionHandler({IllegalArgumentException.class, InvalidProtocolBufferException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(Exception e) {
        return e.getMessage();
    }
}
