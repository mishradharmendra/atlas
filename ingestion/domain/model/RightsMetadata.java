package com.atlas.ingestion.domain.model;

import com.atlas.shared.temporal.AsOf;
import java.time.Instant;
import java.util.Objects;

/**
 * Everything the platform is allowed to do with one acquired document, fixed at acquisition.
 *
 * <p>Travels with the document for its whole life and is consulted before every derivation. A
 * pipeline stage that cannot see the rights metadata must refuse to run rather than assume
 * permission — which is why {@link #forUnlicensedSource} exists and grants nothing.
 *
 * <p>The failure this prevents is quiet and expensive. Absent an explicit record, a pipeline
 * processes whatever it is handed; the exposure is created in bulk, at machine speed, and is
 * discovered months later during a licence audit with no way to establish which derived artefacts
 * are affected.
 */
public record RightsMetadata(
        String sourceId,
        String contractId,
        PermittedUse permittedUse,
        Instant embargoUntil,
        RedistributionClass redistribution,
        String retentionPolicy) {

    public RightsMetadata {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("rights metadata must name its source");
        }
        Objects.requireNonNull(permittedUse, "permitted use");
        Objects.requireNonNull(redistribution, "redistribution class");

        if (redistribution == RedistributionClass.TENANT_PRIVATE
                && permittedUse.permits(Use.TRAIN_MODELS)) {
            // Enforced by the type, not by a config flag, because this is the commitment
            // customers verify in security review.
            throw new IllegalArgumentException(
                    "tenant-private content may never carry training permission");
        }
    }

    /**
     * The safe default when no contract has been recorded for a source.
     *
     * <p>Grants nothing, so the document lands in the immutable store — losing acquired bytes is
     * unrecoverable — but nothing downstream will process it until a contract exists.
     */
    public static RightsMetadata forUnlicensedSource(String sourceId) {
        return new RightsMetadata(
                sourceId, "", PermittedUse.none(), null, RedistributionClass.LICENSED_BROAD, "");
    }

    public static RightsMetadata forTenantContent(String sourceId, String retentionPolicy) {
        return new RightsMetadata(
                sourceId,
                "",
                PermittedUse.forTenantContent(),
                null,
                RedistributionClass.TENANT_PRIVATE,
                retentionPolicy);
    }

    /**
     * Whether a pipeline stage may perform this use at the given point in time.
     *
     * <p>Embargo is checked here as well as in {@code entitlement}, and the duplication is
     * deliberate: entitlement governs whether a principal may <em>see</em> a document, this governs
     * whether the platform may <em>derive</em> from it. An embargoed document can be parsed and
     * stored while remaining invisible, but some contracts forbid deriving anything at all until the
     * embargo lapses.
     */
    public boolean allows(Use use, AsOf at) {
        if (embargoUntil != null && at.instant().isBefore(embargoUntil)) {
            return false;
        }
        return permittedUse.permits(use);
    }

    /** Applies a licence renegotiation, leaving every other permission intact. */
    public RightsMetadata revoke(Use use) {
        return new RightsMetadata(
                sourceId, contractId, permittedUse.revoke(use), embargoUntil, redistribution, retentionPolicy);
    }

    public boolean isEmbargoedAt(AsOf at) {
        return embargoUntil != null && at.instant().isBefore(embargoUntil);
    }
}
