package com.atlas.entitlement.application;

import com.atlas.entitlement.api.EntitlementApi;
import com.atlas.entitlement.api.EntitlementSnapshotView;
import com.atlas.entitlement.domain.model.EntitlementSnapshot;
import com.atlas.entitlement.domain.model.EntitlementTag;
import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.temporal.AsOf;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the module's Open Host Service by translating the aggregate into its wire form.
 *
 * <p>The translation is the point of the class. It keeps the domain model free to change without
 * breaking a consumer in another language, which is exactly the coupling a published aggregate
 * would create.
 */
@Service
class EntitlementApiService implements EntitlementApi {

    private final IssueEntitlementSnapshot issuer;

    EntitlementApiService(IssueEntitlementSnapshot issuer) {
        this.issuer = issuer;
    }

    @Override
    public EntitlementSnapshotView issue(TenantId tenant, PrincipalId principal, AsOf asOf) {
        return toView(issuer.issue(tenant, principal, asOf));
    }

    private static EntitlementSnapshotView toView(EntitlementSnapshot snapshot) {
        return new EntitlementSnapshotView(
                snapshot.id().toString(),
                snapshot.tenant().toString(),
                snapshot.principal().toString(),
                snapshot.asOf().instant(),
                snapshot.expiresAt(),
                toTagViews(snapshot.allow()),
                toTagViews(snapshot.deny()),
                Base64.getEncoder().encodeToString(snapshot.signature()));
    }

    private static List<EntitlementSnapshotView.TagView> toTagViews(List<EntitlementTag> tags) {
        return tags.stream()
                .map(
                        t ->
                                new EntitlementSnapshotView.TagView(
                                        t.dimension().name(), t.value(), t.validFrom(), t.validTo()))
                .toList();
    }
}
