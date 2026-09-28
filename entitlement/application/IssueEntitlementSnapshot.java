package com.atlas.entitlement.application;

import com.atlas.entitlement.domain.model.EntitlementSnapshot;
import com.atlas.entitlement.domain.model.Grant;
import com.atlas.entitlement.domain.model.GrantEffect;
import com.atlas.entitlement.domain.model.SnapshotId;
import com.atlas.entitlement.domain.port.GrantRepository;
import com.atlas.entitlement.domain.port.SnapshotSigner;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues entitlement snapshots.
 *
 * <p>Coordination only: load grants, partition them, sign, return. The rules about what a snapshot
 * means live in {@link EntitlementSnapshot}, which is why this class contains no {@code if} about
 * business state.
 */
@Service
public class IssueEntitlementSnapshot {

    /**
     * How long an issued snapshot stays usable.
     *
     * <p>A trade-off with a wrong answer on both ends. Too long and a revoked licence or a removed
     * ACL keeps working for whoever still holds the token. Too short and long-running agent runs
     * re-issue constantly, adding latency and load to every step.
     *
     * <p>Fifteen minutes bounds exposure to roughly one earnings call while comfortably covering a
     * deep-research run. Runs that outlive it re-issue and, correctly, pick up any revocation.
     */
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    private final GrantRepository grants;
    private final SnapshotSigner signer;
    private final Clock clock;

    public IssueEntitlementSnapshot(GrantRepository grants, SnapshotSigner signer, Clock clock) {
        this.grants = grants;
        this.signer = signer;
        this.clock = clock;
    }

    /**
     * Computes and signs the snapshot authorising this principal at this point in time.
     *
     * <p>Read-only: issuance must never be the thing that fails a request because a write lock was
     * contended. It is on the hot path of every query.
     */
    @Transactional(readOnly = true)
    public EntitlementSnapshot issue(TenantId tenant, PrincipalId principal, AsOf asOf) {
        Instant now = clock.instant();

        if (asOf.instant().isAfter(now)) {
            // A future as-of would authorise grants that have not commenced. Refused rather than
            // clamped, because silently answering a different question than the one asked is
            // worse than refusing the one asked.
            throw new IllegalArgumentException(
                    "cannot issue an entitlement snapshot as-of %s, which is in the future"
                            .formatted(asOf.instant()));
        }

        List<Grant> inForce = grants.findInForce(tenant, principal, asOf);

        var allow = inForce.stream()
                .filter(g -> g.effect() == GrantEffect.ALLOW)
                .filter(g -> g.appliesTo(principal))
                .map(Grant::toTag)
                .toList();

        var deny = inForce.stream()
                .filter(g -> g.effect() == GrantEffect.DENY)
                .filter(g -> g.appliesTo(principal))
                .map(Grant::toTag)
                .toList();

        var unsigned = new EntitlementSnapshot(
                SnapshotId.newId(),
                tenant,
                principal,
                asOf,
                now.plus(LIFETIME),
                allow,
                deny,
                new byte[0]);

        return signer.sign(unsigned);
    }
}
