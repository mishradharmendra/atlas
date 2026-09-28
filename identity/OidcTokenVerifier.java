package com.atlas.identity;

import com.atlas.identity.IdentityProperties.Provider;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies OIDC tokens against an allow-list of issuers.
 *
 * <p>Decoders are built once per issuer and reused, because each one owns a JWKS cache. Building
 * one per request would fetch the key set on every call, which is both a hot loop against the
 * identity provider and an outage here whenever it is slow.
 */
@Component
class OidcTokenVerifier implements IdentityTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenVerifier.class);

    private final Map<String, Trusted> byIssuer = new LinkedHashMap<>();

    OidcTokenVerifier(IdentityProperties properties) {
        for (Provider provider : properties.getProviders()) {
            byIssuer.put(provider.getIssuer(), new Trusted(provider, decoderFor(provider)));
            log.info(
                    "trusting identity provider {} (audience {}, tenant claim {})",
                    provider.getIssuer(),
                    provider.getAudience(),
                    provider.getTenantClaim());
        }
        if (byIssuer.isEmpty()) {
            if (properties.isRequired()) {
                throw new IllegalStateException(
                        "atlas.identity.required is true and no providers are configured. "
                                + "Starting would mean nobody can obtain a credential, which is "
                                + "safer than the alternative but is not a working deployment.");
            }
            log.warn(
                    "no identity provider configured: credential issuance is unauthenticated. "
                            + "Acceptable locally, never in a deployed environment.");
        }
    }

    @Override
    public boolean isConfigured() {
        return !byIssuer.isEmpty();
    }

    @Override
    public VerifiedPrincipal verify(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IdentityNotEstablishedException("no token presented");
        }

        // The issuer is read from the unverified token only to choose which key set to check the
        // signature against. Nothing is trusted until the decoder below has verified it, and an
        // issuer that is not on the allow-list never reaches a decoder at all.
        String claimedIssuer = unverifiedIssuer(bearerToken);
        Trusted trusted = byIssuer.get(claimedIssuer);
        if (trusted == null) {
            // Deliberately does not echo the issuer back: this is an unauthenticated endpoint and
            // the reply should not confirm which issuers are configured.
            log.warn("refused a token from untrusted issuer {}", claimedIssuer);
            throw new IdentityNotEstablishedException("token is not from a trusted issuer");
        }

        Jwt jwt;
        try {
            jwt = trusted.decoder().decode(bearerToken);
        } catch (JwtException invalid) {
            throw new IdentityNotEstablishedException("token rejected: " + invalid.getMessage(), invalid);
        }

        Provider provider = trusted.provider();
        return new VerifiedPrincipal(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString(provider.getTenantClaim()),
                principalOf(jwt, provider),
                jwt.getClaimAsString(provider.getEmailClaim()));
    }

    /** Falls back to the subject, which every OIDC provider issues. */
    private static String principalOf(Jwt jwt, Provider provider) {
        String mapped = jwt.getClaimAsString(provider.getPrincipalClaim());
        return mapped != null && !mapped.isBlank() ? mapped : jwt.getSubject();
    }

    private static String unverifiedIssuer(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException malformed) {
            throw new IdentityNotEstablishedException("token is not a well-formed JWT", malformed);
        }
    }

    /**
     * Builds a decoder for one provider.
     *
     * <p>Discovery when no JWKS uri is given, because the path differs per vendor and hard-coding
     * one would work on Keycloak and fail on Okta. The audience validator is added explicitly:
     * the defaults check issuer and expiry but not audience, so without it a token minted for a
     * different application at the same provider verifies perfectly.
     */
    private static JwtDecoder decoderFor(Provider provider) {
        NimbusJwtDecoder decoder = provider.getJwksUri() == null || provider.getJwksUri().isBlank()
                ? (NimbusJwtDecoder) NimbusJwtDecoder.withIssuerLocation(provider.getIssuer()).build()
                : NimbusJwtDecoder.withJwkSetUri(provider.getJwksUri()).build();

        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(provider.getIssuer());
        if (provider.getAudience() == null || provider.getAudience().isBlank()) {
            decoder.setJwtValidator(standard);
            return decoder;
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                standard, new AudienceValidator(provider.getAudience())));
        return decoder;
    }

    private record Trusted(Provider provider, JwtDecoder decoder) {}
}
