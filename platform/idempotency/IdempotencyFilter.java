package com.atlas.platform.idempotency;

import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes a retried write safe to send twice.
 *
 * <h2>Why this is needed at all</h2>
 *
 * <p>Every create endpoint here mints a fresh identifier per call. A client whose request times
 * out has no way to know whether the write landed, and the correct thing for it to do — retry —
 * produces a second run, a second prediction, a second watchlist. For metering that is money, and
 * the duplicate is indistinguishable from real activity after the fact.
 *
 * <h2>Opt-in, by the caller presenting a key</h2>
 *
 * <p>Requiring the header would break every existing caller at once and would put the platform in
 * the position of rejecting requests that are perfectly safe. A caller that cares about
 * exactly-once presents a key; one that does not gets today's behaviour.
 *
 * <h2>Only successes are recorded</h2>
 *
 * <p>Storing a failed response would make a transient failure permanent for that key: the client
 * retries, which is right, and is handed the same 500 forever. A failure leaves no record, so the
 * retry genuinely re-runs.
 */
@Component
@Order(20) // after TenantContextFilter: the key is scoped to the verified tenant
class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    static final String HEADER = "Idempotency-Key";

    /** How long a key is honoured. Long enough to outlive a client's retry schedule. */
    static final Duration RETENTION = Duration.ofHours(24);

    private static final int MAX_KEY_LENGTH = 255;

    /**
     * Largest body this filter will fingerprint.
     *
     * <p>Bounded because the body is buffered to hash it, and an unbounded buffer on a public
     * endpoint is a memory exhaustion primitive. A request over the limit is refused rather than
     * fingerprinted from a truncated copy, which would make two different bodies share a
     * fingerprint and replay the wrong response.
     */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final IdempotencyStore store;
    private final Clock clock;

    IdempotencyFilter(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || !isWrite(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            response.sendError(
                    400, "idempotency key exceeds " + MAX_KEY_LENGTH + " characters");
            return;
        }

        Optional<RequestTenant> caller = TenantContext.current();
        if (caller.isEmpty()) {
            // No verified tenant means no namespace to scope the key to. Rather than fall back to
            // a global one — which would let a caller present another tenant's key and be handed
            // their response — the request simply proceeds without the guarantee.
            chain.doFilter(request, response);
            return;
        }
        String tenantId = caller.get().tenantId();

        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.sendError(
                    413,
                    "idempotent requests are limited to %d bytes".formatted(MAX_BODY_BYTES));
            return;
        }

        ReplayableRequest cachedRequest = new ReplayableRequest(request, MAX_BODY_BYTES);
        String fingerprint = fingerprint(cachedRequest);

        Optional<IdempotencyRecord> seen = store.find(tenantId, key);
        if (seen.isPresent()) {
            replay(seen.get(), fingerprint, key, response);
            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(cachedRequest, cachedResponse);

        int status = cachedResponse.getStatus();
        String body = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        cachedResponse.copyBodyToResponse();

        if (status < 200 || status > 299) {
            return;
        }

        boolean stored = store.saveIfAbsent(new IdempotencyRecord(
                tenantId, key, fingerprint, status, body, clock.instant()));
        if (!stored) {
            // Two concurrent retries; the other one won. Both did the work, which is the gap this
            // cannot close from here — it needs the key taken before the handler runs, and that
            // is a longer transaction than a filter should hold.
            log.warn(
                    "idempotency key {} for tenant {} was written concurrently; both requests ran",
                    key,
                    tenantId);
        }
    }

    private void replay(
            IdempotencyRecord record,
            String fingerprint,
            String key,
            HttpServletResponse response)
            throws IOException {

        if (!record.requestFingerprint().equals(fingerprint)) {
            // Refused rather than answered. Returning the first response would hand the caller an
            // answer to a question they did not ask, and they would have no way to tell.
            response.sendError(
                    422,
                    "idempotency key '%s' was already used for a different request".formatted(key));
            return;
        }
        response.setStatus(record.responseStatus());
        response.setContentType("application/json");
        response.setHeader("Idempotent-Replay", "true");
        if (record.responseBody() != null) {
            response.getWriter().write(record.responseBody());
        }
    }

    private static boolean isWrite(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /** Method, path and body together: the same key on a different call must not replay. */
    private static String fingerprint(ReplayableRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String query = request.getQueryString();
            if (query != null) {
                digest.update(query.getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) 0);
            digest.update(request.body());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", impossible);
        }
    }
}
