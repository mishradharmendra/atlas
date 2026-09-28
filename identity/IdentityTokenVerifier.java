package com.atlas.identity;

/** Turns a bearer token into a caller whose identity has been vouched for, or refuses. */
public interface IdentityTokenVerifier {

    /**
     * @param bearerToken the raw JWT, without the {@code Bearer } prefix
     * @throws IdentityNotEstablishedException if the token is malformed, expired, addressed to
     *     another audience, signed by a key we cannot resolve, or issued by an untrusted issuer
     */
    VerifiedPrincipal verify(String bearerToken);

    /** Whether any provider is configured. False means the platform cannot authenticate anyone. */
    boolean isConfigured();
}
