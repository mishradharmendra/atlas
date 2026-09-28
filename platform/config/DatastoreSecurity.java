package com.atlas.platform.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Refuses to start against a datastore that would carry licensed content in the clear.
 *
 * <p>The local stack runs OpenSearch with {@code DISABLE_SECURITY_PLUGIN} and Postgres with a
 * password of "atlas", which is correct for something reachable only from a laptop. The danger
 * is that the same configuration is one environment variable away from production, and nothing
 * about a running system says which one it is: an unauthenticated cluster serves queries exactly
 * as well as an authenticated one, right up until somebody else reads it.
 *
 * <p>So this is a startup check rather than a runbook step. A deployment that has not been told
 * it is development must point at TLS endpoints, and if it does not the application refuses to
 * come up — loudly, before a single document is indexed. The failure a check like this prevents
 * is not misconfiguration; it is misconfiguration nobody noticed for six months.
 *
 * <p>It deliberately checks the URLs rather than probing the servers. A probe answers "is this
 * cluster reachable and secured right now", which is a monitoring question; this answers "was
 * this deployment configured to require security at all", which has one right answer and can be
 * settled before anything starts.
 */
@Component
@ConfigurationProperties(prefix = "atlas.security")
public class DatastoreSecurity {

    /**
     * Whether insecure datastore URLs are tolerated.
     *
     * <p>Defaults to permitting them, because the default has to work for the local stack and
     * for every integration test. Production sets this to false, which is the single line that
     * turns the rest of this class on.
     */
    private boolean allowInsecureDatastores = true;

    private String openSearchUrl = "";
    private String datasourceUrl = "";

    public void setAllowInsecureDatastores(boolean allowInsecureDatastores) {
        this.allowInsecureDatastores = allowInsecureDatastores;
    }

    public boolean isAllowInsecureDatastores() {
        return allowInsecureDatastores;
    }

    public void setOpenSearchUrl(String openSearchUrl) {
        this.openSearchUrl = openSearchUrl;
    }

    public void setDatasourceUrl(String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    /**
     * Every way the configured datastores fall short, or an empty list.
     *
     * <p>All of them, not the first: an operator fixing one at a time across a restart cycle
     * that takes minutes will stop reading after the second round trip.
     */
    public List<String> violations() {
        List<String> found = new ArrayList<>();
        if (allowInsecureDatastores) {
            return found;
        }

        if (!openSearchUrl.isBlank() && !isHttps(openSearchUrl)) {
            found.add(
                    ("search index '%s' is not https. Spans carry licensed text and the "
                                    + "entitlement filters that gate it; in the clear, both are "
                                    + "readable by anything on the path.")
                            .formatted(openSearchUrl));
        }

        if (!datasourceUrl.isBlank() && !requiresTls(datasourceUrl)) {
            found.add(
                    ("datasource '%s' does not require TLS. Add `?sslmode=verify-full`. "
                                    + "`require` alone encrypts without authenticating the "
                                    + "server, which stops eavesdropping and not impersonation.")
                            .formatted(datasourceUrl));
        }

        return found;
    }

    private static boolean isHttps(String url) {
        try {
            return "https".equalsIgnoreCase(URI.create(url).getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * JDBC URLs are not URIs, so this reads the query string rather than parsing.
     *
     * <p>`sslmode=require` is deliberately not enough. It encrypts the connection and does not
     * check who is on the other end, so it defends against a passive listener and not against
     * the attack that matters on a shared network.
     */
    private static boolean requiresTls(String jdbcUrl) {
        String lower = jdbcUrl.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("sslmode=verify-full") || lower.contains("sslmode=verify-ca");
    }
}
