package com.atlas.identity;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Refuses a token addressed to a different application.
 *
 * <p>Spring's default validators check issuer and expiry and stop there. Without this, a token
 * minted by the same provider for any other application in the estate verifies perfectly here —
 * correct signature, trusted issuer, unexpired — and the holder of a token for an unrelated
 * internal tool can mint an Atlas credential with it.
 */
record AudienceValidator(String audience) implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "token audience %s does not include %s".formatted(token.getAudience(), audience),
                null));
    }
}
