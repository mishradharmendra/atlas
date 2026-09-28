package com.atlas.entitlement.domain.model;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.time.Instant;
import java.util.Objects;

/**
 * A durable authorisation record: the input from which a snapshot is computed.
 *
 * <p>Grants live for months and change through contract negotiation. Snapshots live for
 * minutes and are computed from whichever grants were in force. Keeping them as separate
 * types is what makes a snapshot explicable after the fact — "why did this run see that document?"
 * is answered by replaying the grants that were active at the run's as-of, rather than by
 * inspecting mutable current state that has since moved on.
 *
 * <p>A grant may be a permission or a prohibition. Prohibitions carry information barriers and
 * legal holds, and {@link EntitlementSnapshot} evaluates them first and unconditionally.
 */
public record Grant(
        GrantId id,
        TenantId tenant,
        PrincipalId principal,
        EntitlementDimension dimension,
        String value,
        GrantEffect effect,
        Instant effectiveFrom,
        Instant effectiveTo,
        String contractId) {

    public Grant {
        Objects.requireNonNull(id, "grant id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(effect, "effect");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("grant must carry a value");
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "grant %s ends (%s) before it begins (%s)".formatted(id, effectiveTo, effectiveFrom));
        }
    }

    /**
     * Whether this grant applies to the whole tenant rather than one principal.
     *
     * <p>Firm-wide research licences are held at tenant level; a deal-team information barrier is
     * held per principal. Both must resolve in one pass, so the null principal is modelled rather
     * than split into two types.
     */
    public boolean isTenantWide() {
        return principal == null;
    }

    public boolean appliesTo(PrincipalId candidate) {
        return isTenantWide() || principal.equals(candidate);
    }

    /** Whether the grant is in force at the instant the question is being asked about. */
    public boolean isInForceAt(AsOf asOf) {
        Instant at = asOf.instant();
        if (effectiveFrom != null && at.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !at.isAfter(effectiveTo);
    }

    /**
     * Projects to the tag form the retrieval plane consumes.
     *
     * <p>The validity window is carried through rather than collapsed, so the snapshot can
     * distinguish "not granted" from "granted but embargoed" — a distinction that decides whether
     * the user is told the content does not exist or that it becomes available on a known date.
     */
    public EntitlementTag toTag() {
        return new EntitlementTag(dimension, value, effectiveFrom, effectiveTo);
    }
}
