package com.atlas.entitlement.adapter.in.web;

import com.atlas.contracts.v1.AsOf;
import com.atlas.contracts.v1.EntitlementDimension;
import com.atlas.contracts.v1.EntitlementSnapshot;
import com.atlas.contracts.v1.EntitlementTag;
import com.atlas.contracts.v1.IssueSnapshotResponse;
import com.atlas.contracts.v1.PrincipalId;
import com.atlas.contracts.v1.TenantId;
import com.atlas.entitlement.api.EntitlementSnapshotView;
import com.atlas.entitlement.domain.model.GrantEffect;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.Base64;

/**
 * Anti-corruption layer between the domain's view type and the published language.
 *
 * <p>This is the only place in the JVM plane where a generated contract type is constructed, and
 * the reason it exists rather than the domain simply returning protobuf: a protobuf builder has no
 * invariants. Every field is optional, every message has a no-arg default, and
 * {@code EntitlementSnapshot.newBuilder().build()} is a valid object that authorises nothing and
 * claims to be a snapshot. Letting that type into the model would trade every guarantee in this
 * codebase for the convenience of not writing this class.
 *
 * <p>The translation is deliberately total and explicit. A silently dropped field here is a grant
 * that does not reach the retrieval plane, which reads downstream as a licence the customer does
 * not have.
 */
final class EntitlementProtoMapper {

    private EntitlementProtoMapper() {}

    static IssueSnapshotResponse toProto(EntitlementSnapshotView view) {
        EntitlementSnapshot.Builder snapshot = EntitlementSnapshot.newBuilder()
                .setSnapshotId(view.snapshotId())
                .setTenant(TenantId.newBuilder().setValue(view.tenantId()))
                .setPrincipal(PrincipalId.newBuilder().setValue(view.principalId()))
                .setAsOf(AsOf.newBuilder().setInstant(view.asOf().toString()))
                .setExpiresAt(view.expiresAt().toString())
                .setSignature(ByteString.copyFrom(Base64.getDecoder().decode(view.signature())));

        view.allow().stream().map(EntitlementProtoMapper::toProto).forEach(snapshot::addAllow);
        view.deny().stream().map(EntitlementProtoMapper::toProto).forEach(snapshot::addDeny);

        return IssueSnapshotResponse.newBuilder().setSnapshot(snapshot).build();
    }

    private static EntitlementTag toProto(EntitlementSnapshotView.TagView tag) {
        EntitlementTag.Builder proto = EntitlementTag.newBuilder()
                .setDimension(dimension(tag.dimension()))
                .setValue(tag.value());

        // An unbounded window is the empty string, not the epoch. The distinction is
        // load-bearing: a grant with no start date is in force always, while one starting at
        // the epoch is in force from 1970 -- which reads the same until somebody asks an
        // as-of question about a date before the grant was written.
        if (tag.validFrom() != null) {
            proto.setValidFrom(tag.validFrom().toString());
        }
        if (tag.validTo() != null) {
            proto.setValidTo(tag.validTo().toString());
        }
        return proto.build();
    }

    /**
     * Maps a domain dimension name onto its contract enum.
     *
     * <p>Throws on an unrecognised name rather than falling back to {@code UNSPECIFIED}. A
     * dimension the wire cannot express is a filter the retrieval plane will not apply, and
     * failing to apply an entitlement filter is the one error this system must never make
     * quietly.
     */
    private static EntitlementDimension dimension(String name) {
        EntitlementDimension value =
                EntitlementDimension.valueOf("ENTITLEMENT_DIMENSION_" + name);
        if (value == EntitlementDimension.ENTITLEMENT_DIMENSION_UNSPECIFIED) {
            throw new IllegalStateException(
                    "entitlement dimension '" + name + "' has no representation in the published "
                            + "language; a filter that cannot be expressed cannot be enforced");
        }
        return value;
    }

    /**
     * Maps a contract dimension back onto the domain's.
     *
     * <p>Refuses {@code UNSPECIFIED} rather than choosing one. A grant recorded against a
     * dimension nobody named is a licence whose scope is whatever the reader assumes.
     */
    static com.atlas.entitlement.domain.model.EntitlementDimension dimension(
            EntitlementDimension wire) {
        if (wire == EntitlementDimension.ENTITLEMENT_DIMENSION_UNSPECIFIED
                || wire == EntitlementDimension.UNRECOGNIZED) {
            throw new IllegalArgumentException(
                    "a grant must name the dimension it applies along");
        }
        return com.atlas.entitlement.domain.model.EntitlementDimension.valueOf(
                wire.name().replace("ENTITLEMENT_DIMENSION_", ""));
    }

    /**
     * Maps the effect, refusing an unstated one.
     *
     * <p>Defaulting would have to pick, and both choices are wrong: defaulting to ALLOW grants
     * something nobody asked for, defaulting to DENY writes a barrier nobody asked for.
     */
    static GrantEffect effect(com.atlas.contracts.v1.GrantEffect wire) {
        return switch (wire) {
            case GRANT_EFFECT_ALLOW -> GrantEffect.ALLOW;
            case GRANT_EFFECT_DENY -> GrantEffect.DENY;
            case GRANT_EFFECT_UNSPECIFIED, UNRECOGNIZED ->
                throw new IllegalArgumentException(
                        "a grant must state whether it permits or prohibits");
        };
    }

    /** Parses the request side. Absent as-of is the caller's; defaulting happens in the adapter. */
    static Instant asOf(AsOf asOf) {
        return asOf.getInstant().isEmpty() ? null : Instant.parse(asOf.getInstant());
    }
}
