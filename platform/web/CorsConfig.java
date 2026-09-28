package com.atlas.platform.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cross-origin access for the workspace.
 *
 * <p>The browser is the only client that needs this, which is why it was missing: curl, the
 * Python plane and every integration test talk to the platform without a preflight, so the API
 * looked entirely healthy while no web page could reach it.
 *
 * <p>Origins are listed, never wildcarded. `*` cannot be combined with credentials at all, and a
 * wildcard on a tenant-scoped API would let any page on the internet make authenticated calls
 * with a snapshot it tricked out of a user.
 */
@Configuration
@ConfigurationProperties(prefix = "atlas.web")
public class CorsConfig {

    /** Empty by default: a deployment that needs cross-origin access says so. */
    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    CorsFilter corsFilter() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // The credential headers the workspace actually sends. Listing them rather than allowing
        // everything keeps a new header a deliberate act.
        cors.setAllowedHeaders(
                List.of("Content-Type", "Authorization", "X-Atlas-Snapshot", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Idempotent-Replay"));
        // No cookies are used; the snapshot travels in a header. Leaving this false means a
        // malicious page cannot make an authenticated call by riding on an existing session.
        cors.setAllowCredentials(false);
        cors.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsFilter(source);
    }
}
