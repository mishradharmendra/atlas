package com.atlas.identity;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The identity providers this deployment trusts.
 *
 * <p>A list rather than a single provider, because a migration from one to another is the normal
 * case and a cutover that requires both to be unavailable at once is not a migration anybody
 * will agree to run. Local Keycloak and Okta can be trusted simultaneously; removing one is
 * deleting an entry.
 */
@ConfigurationProperties(prefix = "atlas.identity")
public class IdentityProperties {

    /**
     * Trusted issuers. An empty list means no OIDC provider is configured.
     *
     * <p>Whether that is fatal is deliberately not decided here — see {@code required}.
     */
    private List<Provider> providers = new ArrayList<>();

    /**
     * Whether the platform refuses to start without a provider.
     *
     * <p>Defaults to false so a developer can run the platform and the test suite without
     * standing up an identity provider. It is set true in every deployed environment, because
     * the failure it prevents — shipping with token verification quietly disabled — is one that
     * looks exactly like a working system.
     */
    private boolean required = false;

    public List<Provider> getProviders() {
        return providers;
    }

    public void setProviders(List<Provider> providers) {
        this.providers = providers;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    /** One trusted identity provider. */
    public static class Provider {

        /** The exact {@code iss} claim to trust. The allow-list entry. */
        private String issuer;

        /**
         * Where the signing keys live.
         *
         * <p>Optional. Left unset, the issuer is resolved by OIDC discovery, which is the
         * provider-agnostic route and the reason this code does not care whether it is talking
         * to Keycloak or Okta. Guessing a path instead would bake one vendor's URL layout into
         * the platform and break on the other.
         */
        private String jwksUri;

        /**
         * The audience this platform is known by at that provider.
         *
         * <p>Checked, because a token minted for a different application at the same provider is
         * a valid token with a correct signature, and accepting it lets any app in the estate
         * mint credentials here.
         */
        private String audience;

        /**
         * Claim carrying the Atlas tenant id.
         *
         * <p>Configurable because providers disagree: Keycloak puts it wherever the realm's
         * mapper is told to, Okta typically in a custom claim or an org attribute. Naming it in
         * configuration keeps the difference out of the code.
         */
        private String tenantClaim = "atlas_tenant";

        /** Claim carrying the Atlas principal id. Falls back to {@code sub}. */
        private String principalClaim = "atlas_principal";

        private String emailClaim = "email";

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getTenantClaim() {
            return tenantClaim;
        }

        public void setTenantClaim(String tenantClaim) {
            this.tenantClaim = tenantClaim;
        }

        public String getPrincipalClaim() {
            return principalClaim;
        }

        public void setPrincipalClaim(String principalClaim) {
            this.principalClaim = principalClaim;
        }

        public String getEmailClaim() {
            return emailClaim;
        }

        public void setEmailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
        }
    }
}
