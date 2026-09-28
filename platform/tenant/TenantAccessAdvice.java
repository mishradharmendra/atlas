package com.atlas.platform.tenant;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps a cross-tenant attempt to 403, everywhere.
 *
 * <p>This was a per-controller {@code @ExceptionHandler} on the one controller that had been
 * written when the rule was introduced. The next controller to check a tenant got a 500 instead —
 * the check worked, the refusal was correct, and the caller saw an error that reads like an
 * outage. Exactly the failure mode that moved authentication into a filter: a rule enforced
 * controller by controller is one that is missing from whichever controller is written next.
 *
 * <p>403 rather than 404 deliberately. Hiding existence sounds safer, but the caller here has
 * already authenticated as somebody, and telling them "not yours" is both true and useful, while
 * a 404 sends them to support to chase a deliverable the system is pretending it lost.
 */
@RestControllerAdvice
class TenantAccessAdvice {

    private static final Logger log = LoggerFactory.getLogger(TenantAccessAdvice.class);

    @ExceptionHandler(TenantMismatchException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> refused(TenantMismatchException e) {
        // Logged at warn: a cross-tenant attempt is either an attack or a bug, and both want
        // someone to notice.
        log.warn(
                "refused cross-tenant request: authenticated as {}, asked for {}",
                e.authenticatedAs(),
                e.requested());
        return Map.of(
                "message", "this resource belongs to another tenant",
                "authenticatedAs", e.authenticatedAs());
    }

    @ExceptionHandler(UnauthenticatedRequestException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> unauthenticated(UnauthenticatedRequestException e) {
        return Map.of("message", e.getMessage());
    }
}
