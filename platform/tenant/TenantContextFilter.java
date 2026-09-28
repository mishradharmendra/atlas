package com.atlas.platform.tenant;

import com.atlas.entitlement.api.SnapshotAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the request's verified identity, and refuses the request if there is none.
 *
 * <h2>Authenticated by default, public by exception</h2>
 *
 * <p>The first version established an identity when a credential was present and let the request
 * through when it was not, leaving each endpoint to decide. That is the same mistake as a
 * per-controller check, one step further back: five of six controllers never made the decision,
 * so anyone could publish a skill, propose a graph edge or register a document. None of those
 * leak another tenant's data, and all of them are writes by an unauthenticated caller.
 *
 * <p>So the default is refusal and the exceptions are listed here, where adding one is a visible
 * act rather than an omission. The list is short on purpose:
 *
 * <ul>
 *   <li><b>Snapshot issuance</b> — a credential cannot be required to obtain a credential. This
 *       is the trust boundary, and it needs an upstream identity provider that does not exist
 *       yet. It is the largest remaining hole and is recorded as one.
 *   <li><b>Actuator</b> — liveness must answer before anything else works, or a failing
 *       authenticator takes the health check down with it and an orchestrator cannot tell an
 *       unhealthy pod from a misconfigured one.
 * </ul>
 */
@Component
@Order(10) // before IdempotencyFilter, which scopes its keys to the tenant established here
class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    /** The presented credential. Named for what it is rather than reusing {@code Authorization}. */
    static final String HEADER = "X-Atlas-Snapshot";

    /**
     * Paths that answer without a tenant credential. Every addition is a deliberate widening.
     *
     * <p>Neither is unauthenticated. {@code /snapshots} is where a credential is obtained, so
     * requiring one would have no answer. {@code /grants} is administrative and carries a
     * stronger credential than a snapshot: an administrator is not a tenant user, and making one
     * hold a tenant snapshot would mean the right to administer is something a tenant session
     * could carry.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator", "/api/v1/entitlement/snapshots", "/api/v1/entitlement/grants");

    private final SnapshotAuthenticator authenticator;
    private final Clock clock;

    TenantContextFilter(SnapshotAuthenticator authenticator, Clock clock) {
        this.authenticator = authenticator;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Optional<RequestTenant> identity = authenticator
                    .authenticate(request.getHeader(HEADER), clock.instant())
                    .map(verified -> new RequestTenant(
                            verified.tenantId(),
                            verified.principalId(),
                            verified.snapshotId(),
                            verified.deniedLabels()));

            identity.ifPresent(TenantContext::set);

            if (identity.isEmpty() && !isPublic(request) && !isPreflight(request)) {
                // Warn: on a healthy system this is a misconfigured client, and on an unhealthy
                // one it is somebody trying the doors. Both are worth seeing.
                log.warn(
                        "refused unauthenticated {} {}",
                        request.getMethod(),
                        request.getRequestURI());
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "this endpoint requires a signed entitlement snapshot in " + HEADER);
                return;
            }

            chain.doFilter(request, response);
        } finally {
            // Always, on every path. Request threads are pooled, and a stale identity left on a
            // reused thread serves one tenant's data to the next caller.
            TenantContext.clear();
        }
    }

    private static boolean isPublic(HttpServletRequest request) {
        return PUBLIC_PATHS.stream().anyMatch(request.getRequestURI()::startsWith);
    }

    /**
     * A CORS preflight, which browsers send without credentials by specification.
     *
     * <p>Demanding one here refuses every browser client permanently and invisibly to anything
     * that is not a browser: curl and the integration tests never send a preflight, so the
     * endpoint looks perfectly healthy while the workspace cannot reach it. Nothing is exposed by
     * answering — the preflight carries no body and the real request that follows is still
     * subject to the check above.
     */
    private static boolean isPreflight(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;
    }
}
