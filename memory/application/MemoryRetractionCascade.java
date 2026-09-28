package com.atlas.memory.application;

import com.atlas.catalog.events.DocumentRetracted;
import com.atlas.memory.api.MemoryApi;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The third place a withdrawn document survives, and the quietest.
 *
 * <p>The index purge is visible in a document count. The deliverable flag is visible to whoever
 * sent it. An agent that remembers a figure shows neither: the artifact says nothing, the index
 * no longer holds the span, and the next run asserts it from recall with no citation a reader
 * could follow back to a document that no longer exists.
 *
 * <p>G2.11 was this same failure one layer down — a retraction that looked applied and was not,
 * because the mechanism that was supposed to undo it returned success without doing anything.
 * The lesson recorded there was that a takedown must be verified at every place the content
 * came to rest, so this subscribes at the same time as the aggregate it protects was written.
 */
@Component
class MemoryRetractionCascade {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetractionCascade.class);

    private final MemoryApi memory;

    MemoryRetractionCascade(MemoryApi memory) {
        this.memory = memory;
    }

    /** No {@code @Transactional}: {@code @ApplicationModuleListener} already implies REQUIRES_NEW. */
    @ApplicationModuleListener
    void on(DocumentRetracted event) {
        int forgotten = memory.forget(
                Set.of(event.documentId()),
                "source document %s was withdrawn: %s"
                        .formatted(event.documentId(), event.reason()));

        if (forgotten > 0) {
            log.warn(
                    "retraction of {} withdrew {} memory entrie(s); agents will stop asserting "
                            + "what they learned from it",
                    event.documentId(),
                    forgotten);
        }
    }
}
