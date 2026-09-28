package com.atlas.identity;

import java.util.Objects;

/**
 * A caller whose identity an identity provider has vouched for.
 *
 * <p>Every field here came out of a signature-verified token. None of it came from the request
 * body, which is the entire distinction between this type and what the platform trusted before.
 */
public record VerifiedPrincipal(
        String issuer, String subject, String tenantId, String principalId, String email) {

    public VerifiedPrincipal {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");

        // Refused rather than defaulted. A token from a correctly configured provider that
        // happens to carry no tenant claim is the case where a default would silently put a
        // stranger into somebody's tenant, and it would look like a successful login.
        if (tenantId == null || tenantId.isBlank()) {
            throw new IdentityNotEstablishedException(
                    ("token from %s for subject %s carries no tenant claim. There is no safe "
                                    + "default: putting them in a fallback tenant is either an "
                                    + "empty account or somebody else's data")
                            .formatted(issuer, subject));
        }
        if (principalId == null || principalId.isBlank()) {
            throw new IdentityNotEstablishedException(
                    "token from %s carries no usable principal claim".formatted(issuer));
        }
    }
}
