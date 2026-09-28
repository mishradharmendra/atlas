package com.atlas.platform.config;

import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Stops the application before it serves anything from an insecure datastore.
 *
 * <p>Thrown from {@code ApplicationReadyEvent} rather than logged, because a warning about this
 * is a warning nobody reads. The whole value of the check is that a misconfigured deployment
 * never reaches a state where it looks healthy.
 */
@Component
class DatastoreSecurityGate {

    private final DatastoreSecurity security;

    DatastoreSecurityGate(DatastoreSecurity security) {
        this.security = security;
    }

    @EventListener(ApplicationReadyEvent.class)
    void refuseInsecureDatastores() {
        List<String> violations = security.violations();
        if (violations.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "atlas.security.allow-insecure-datastores is false and the configured datastores "
                        + "are not secure:\n  - "
                        + String.join("\n  - ", violations));
    }
}
