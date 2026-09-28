package com.atlas.entitlement.domain.model;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, expiring authorisation to read a defined slice of the corpus.
 *
 * <h2>Contract</h2>
 *
 * <p>A snapshot is a pure function of {@code (tenant, principal, asOf)}. Issued by this context,
 * consumed by the Python retrieval plane, the agent's context assembler and memory retrieval
 * alike — the same token gates all three, because an agent recalling another tenant's prior work
 * from memory is a breach, not a bug, and memory retrieval is exactly as dangerous as document
 * retrieval.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Immutable once issued.</b> No mutator exists. A changed grant produces a new snapshot;
 *       it never edits one in flight, so a run's evidence set stays explicable after the fact.
 *   <li><b>Expiring.</b> Without expiry, a revoked licence or a removed ACL could be exploited
 *       indefinitely by holding an old token.
 *   <li><b>Deny beats allow.</b> Unconditionally. Information barriers and legal holds are
 *       expressed as denials and must not be defeatable by adding a grant.
 *   <li><b>Bounded by its own as-of.</b> A snapshot computed for May cannot authorise a September
 *       question, because grants that lapsed in between would be wrongly honoured.
 * </ol>
 *
 * <p>Everything here fails closed. There is no code path that turns an invalid snapshot into an
 * unfiltered search.
 */
public record EntitlementSnapshot(
        SnapshotId id,
        TenantId tenant,
        PrincipalId principal,
        AsOf asOf,
        Instant expiresAt,
        List<EntitlementTag> allow,
        List<EntitlementTag> deny,
        byte[] signature) {

    public EntitlementSnapshot {
        Objects.requireNonNull(id, "snapshot id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(asOf, "as-of");
        Objects.requireNonNull(expiresAt, "expiry");

        if (!expiresAt.isAfter(asOf.instant())) {
            throw new IllegalArgumentException(
                    "snapshot expires at %s, at or before its own as-of %s"
                            .formatted(expiresAt, asOf.instant()));
        }

        allow = List.copyOf(Objects.requireNonNullElse(allow, List.of()));
        deny = List.copyOf(Objects.requireNonNullElse(deny, List.of()));
        signature = signature == null ? new byte[0] : signature.clone();
    }

    /**
     * Whether this snapshot may be used to answer a question dated {@code queryAsOf}, at wall-clock
     * time {@code now}.
     *
     * <p>Two separate checks. Expiry is about revocation safety and uses real time. The as-of bound
     * is about correctness: honouring a September question with a May snapshot would apply grants
     * that may since have lapsed.
     */
    public boolean isUsableFor(AsOf queryAsOf, Instant now) {
        return now.isBefore(expiresAt) && !queryAsOf.instant().isAfter(asOf.instant());
    }

    /** Fail-closed guard for call sites that must not proceed without a valid snapshot. */
    public void requireUsableFor(AsOf queryAsOf, Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "entitlement snapshot %s expired at %s".formatted(id, expiresAt));
        }
        if (queryAsOf.instant().isAfter(asOf.instant())) {
            throw new IllegalStateException(
                    "entitlement snapshot %s was computed as-of %s and cannot authorise a query "
                            .formatted(id, asOf.instant())
                            + "as-of " + queryAsOf.instant());
        }
    }

    /**
     * Decide access to content carrying the given tag value on the given dimension.
     *
     * <p>Order is significant and not an optimisation: deny is evaluated first and wins outright.
     */
    public AccessDecision decide(EntitlementDimension dimension, String value, AsOf queryAsOf) {
        for (EntitlementTag tag : deny) {
            if (tag.matches(dimension, value) && tag.appliesAt(queryAsOf)) {
                return AccessDecision.DENIED_BY_RULE;
            }
        }

        boolean matchedButNotYetInForce = false;
        for (EntitlementTag tag : allow) {
            if (tag.matches(dimension, value)) {
                if (tag.appliesAt(queryAsOf)) {
                    return AccessDecision.PERMITTED;
                }
                matchedButNotYetInForce = true;
            }
        }

        return matchedButNotYetInForce
                ? AccessDecision.DENIED_EMBARGOED
                : AccessDecision.DENIED_NOT_GRANTED;
    }

    public boolean permits(EntitlementDimension dimension, String value, AsOf queryAsOf) {
        return decide(dimension, value, queryAsOf).isPermitted();
    }

    /**
     * The allow-tags in force at {@code queryAsOf}, which the retrieval plane compiles into an
     * index pre-filter expression.
     *
     * <p>Embargoed tags are excluded here rather than filtered later, so the pre-filter reflects
     * exactly what the principal may see at that instant.
     */
    public List<EntitlementTag> effectiveAllow(AsOf queryAsOf) {
        return allow.stream().filter(tag -> tag.appliesAt(queryAsOf)).toList();
    }

    public List<EntitlementTag> effectiveDeny(AsOf queryAsOf) {
        return deny.stream().filter(tag -> tag.appliesAt(queryAsOf)).toList();
    }

    /**
     * A principal with no grants in force. Returned rather than null when a lookup finds nothing,
     * so the fail-closed path is the default one and callers cannot forget to handle it.
     */
    public static EntitlementSnapshot empty(
            TenantId tenant, PrincipalId principal, AsOf asOf, Instant expiresAt) {
        return new EntitlementSnapshot(
                SnapshotId.newId(), tenant, principal, asOf, expiresAt, List.of(), List.of(), new byte[0]);
    }

    /** This snapshot with a signature attached. Used by {@code SnapshotSigner}. */
    public EntitlementSnapshot withSignature(byte[] newSignature) {
        return new EntitlementSnapshot(
                id, tenant, principal, asOf, expiresAt, allow, deny, newSignature);
    }

    /**
     * Deterministic byte encoding of everything the signature must cover.
     *
     * <p>Two properties matter and both are easy to get wrong.
     *
     * <p><b>Determinism.</b> The same snapshot must encode identically on every machine and every
     * JVM version, or verification fails intermittently in production and passes locally. Tags are
     * therefore sorted explicitly rather than relying on insertion order.
     *
     * <p><b>Unambiguous framing.</b> Fields are length-prefixed. Simple concatenation lets two
     * different snapshots produce identical bytes — a grant on source {@code "ab"} and one on
     * {@code "a"} followed by a field starting {@code "b"} would collide, and a collision is a
     * forged entitlement.
     *
     * <p><b>Instants are epoch milliseconds, not formatted strings.</b> This one is a cross-
     * language trap. {@code Instant.toString()} renders {@code 2026-07-15T12:00:00Z} while
     * Python's {@code isoformat()} renders {@code 2026-07-15T12:00:00+00:00}, and Java omits
     * trailing zero subsecond digits where other languages keep them. Every one of those
     * differences produces a signature that verifies in Java, fails in Python, and is discovered
     * only after deployment. An integer has no formatting.
     */
    public byte[] canonicalBytes() {
        var out = new StringBuilder();
        appendField(out, id.toString());
        appendField(out, tenant.toString());
        appendField(out, principal.toString());
        appendField(out, Long.toString(asOf.instant().toEpochMilli()));
        appendField(out, Long.toString(expiresAt.toEpochMilli()));
        appendField(out, "allow");
        allow.stream().map(EntitlementSnapshot::encodeTag).sorted().forEach(t -> appendField(out, t));
        appendField(out, "deny");
        deny.stream().map(EntitlementSnapshot::encodeTag).sorted().forEach(t -> appendField(out, t));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendField(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append('|');
    }

    private static String encodeTag(EntitlementTag tag) {
        return "%s/%s/%s/%s"
                .formatted(
                        tag.dimension().name(),
                        tag.value(),
                        tag.validFrom() == null ? "-" : Long.toString(tag.validFrom().toEpochMilli()),
                        tag.validTo() == null ? "-" : Long.toString(tag.validTo().toEpochMilli()));
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    // Arrays break record equality, so both accessors below are overridden to compare by content.
    @Override
    public boolean equals(Object other) {
        return other instanceof EntitlementSnapshot s && id.equals(s.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
