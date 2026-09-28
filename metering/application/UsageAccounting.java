package com.atlas.metering.application;

import com.atlas.metering.domain.model.UsageRecord;
import com.atlas.metering.domain.port.UsageLedger;
import com.atlas.research.events.StepCostIncurred;
import com.atlas.shared.identity.TenantId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Writes every step's cost into the ledger as it is incurred.
 *
 * <p>Subscribes rather than being called, so that adding metering did not require a change to the
 * research context. That matters beyond tidiness: accounting is exactly the concern that tends to
 * get bolted into the middle of a use case, and once it is there the use case cannot be tested
 * without it.
 */
@Component
class UsageAccounting {

    private static final Logger log = LoggerFactory.getLogger(UsageAccounting.class);

    private final UsageLedger ledger;

    UsageAccounting(UsageLedger ledger) {
        this.ledger = ledger;
    }

    /** No {@code @Transactional}: {@code @ApplicationModuleListener} already implies REQUIRES_NEW. */
    @ApplicationModuleListener
    void on(StepCostIncurred event) {
        ledger.record(new UsageRecord(
                new TenantId(UUID.fromString(event.tenantId())),
                event.runId(),
                event.stepId(),
                event.attempt(),
                event.tokens(),
                event.costMicros(),
                event.at()));

        log.debug(
                "metered {} micros for step {} of run {}",
                event.costMicros(),
                event.stepId(),
                event.runId());
    }
}
