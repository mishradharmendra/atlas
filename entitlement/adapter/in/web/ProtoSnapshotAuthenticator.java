package com.atlas.entitlement.adapter.in.web;

import com.atlas.contracts.v1.EntitlementDimension;
import com.atlas.contracts.v1.IssueSnapshotResponse;
import com.atlas.entitlement.api.SnapshotAuthenticator;
import com.atlas.entitlement.api.VerifiedIdentity;
import com.atlas.entitlement.domain.model.EntitlementSnapshot;
import com.atlas.entitlement.domain.model.EntitlementTag;
import com.atlas.entitlement.domain.model.SnapshotId;
import com.atlas.entitlement.domain.port.SnapshotSigner;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import com.google.protobuf.util.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies a presented snapshot.
 *
 * <p>An adapter because it touches generated contract types, which the architecture rules confine
 * to this layer. The crypto and the parsing stay inside the entitlement module; what leaves is a
 * {@link VerifiedIdentity}, which carries no grants — an authenticator's job is to say who the
 * caller is, not what they may see.
 *
 * <p>Every failure path returns empty and logs at warn. There is deliberately no branch that
 * treats a malformed credential as an absent one and lets the request continue: that is how an
 * authentication bypass is written by accident.
 */
@Component
class ProtoSnapshotAuthenticator implements SnapshotAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ProtoSnapshotAuthenticator.class);
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private final SnapshotSigner signer;

    ProtoSnapshotAuthenticator(SnapshotSigner signer) {
        this.signer = signer;
    }

    @Override
    public Optional<VerifiedIdentity> authenticate(String presented, Instant now) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }

        EntitlementSnapshot snapshot;
        try {
            IssueSnapshotResponse.Builder wire = IssueSnapshotResponse.newBuilder();
            PARSER.merge(presented, wire);
            snapshot = toDomain(wire.build());
        } catch (Exception malformed) {
            log.warn("rejected a malformed entitlement snapshot: {}", malformed.getMessage());
            return Optional.empty();
        }

        if (!signer.verify(snapshot)) {
            // The case that matters: a caller editing the tenant field of a snapshot it holds.
            log.warn(
                    "rejected snapshot {} for tenant {}: signature does not match",
                    snapshot.id(),
                    snapshot.tenant());
            return Optional.empty();
        }
        if (!now.isBefore(snapshot.expiresAt())) {
            log.warn("rejected snapshot {}: expired at {}", snapshot.id(), snapshot.expiresAt());
            return Optional.empty();
        }

        return Optional.of(new VerifiedIdentity(
                snapshot.id().value().toString(),
                snapshot.tenant().value().toString(),
                snapshot.principal().value().toString(),
                snapshot.expiresAt(),
                // Carried forward so a guardrail can ask what this credential is barred from,
                // not only what it permits. A skill forbidden to commingle MNPI needs to know
                // the caller cannot see MNPI, and "was not granted" is a weaker statement than
                // "was denied" -- only the second survives a grant being added later.
                snapshot.deny().stream()
                        .filter(tag -> tag.dimension()
                                == com.atlas.entitlement.domain.model.EntitlementDimension
                                        .SECURITY_LABEL)
                        .map(tag -> tag.value().toLowerCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }

    private static EntitlementSnapshot toDomain(IssueSnapshotResponse response) {
        com.atlas.contracts.v1.EntitlementSnapshot wire = response.getSnapshot();
        return new EntitlementSnapshot(
                SnapshotId.of(wire.getSnapshotId()),
                TenantId.of(wire.getTenant().getValue()),
                PrincipalId.of(wire.getPrincipal().getValue()),
                AsOf.at(Instant.parse(wire.getAsOf().getInstant())),
                Instant.parse(wire.getExpiresAt()),
                tags(wire.getAllowList()),
                tags(wire.getDenyList()),
                wire.getSignature().toByteArray());
    }

    private static List<EntitlementTag> tags(List<com.atlas.contracts.v1.EntitlementTag> wire) {
        return wire.stream().map(ProtoSnapshotAuthenticator::tag).toList();
    }

    private static EntitlementTag tag(com.atlas.contracts.v1.EntitlementTag wire) {
        return new EntitlementTag(
                dimension(wire.getDimension()),
                wire.getValue(),
                wire.getValidFrom().isBlank() ? null : Instant.parse(wire.getValidFrom()),
                wire.getValidTo().isBlank() ? null : Instant.parse(wire.getValidTo()));
    }

    private static com.atlas.entitlement.domain.model.EntitlementDimension dimension(
            EntitlementDimension wire) {
        return com.atlas.entitlement.domain.model.EntitlementDimension.valueOf(
                wire.name().replace("ENTITLEMENT_DIMENSION_", ""));
    }
}
