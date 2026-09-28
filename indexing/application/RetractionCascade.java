package com.atlas.indexing.application;

import com.atlas.catalog.events.DocumentRetracted;
import com.atlas.indexing.domain.port.SearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The first hop of the takedown cascade.
 *
 * <p>{@code @ApplicationModuleListener} rather than a plain listener, which buys three things that
 * matter here: the publication is written in the retraction's transaction, the handler runs after
 * that transaction commits, and the registry retains the entry until the handler returns.
 *
 * <p>So a crash mid-cascade leaves an incomplete publication that is retried, rather than a
 * document marked withdrawn in the catalog and still answering queries. Exceptions are allowed to
 * propagate for the same reason — swallowing one here would mark the cascade complete while the
 * content is still live, and nothing downstream would ever notice.
 */
@Component
class RetractionCascade {

    private static final Logger log = LoggerFactory.getLogger(RetractionCascade.class);

    private final SearchIndex index;

    RetractionCascade(SearchIndex index) {
        this.index = index;
    }

    @ApplicationModuleListener
    void on(DocumentRetracted event) {
        int purged = index.purgeDocument(event.documentId());

        // Logged at info because this is an auditable compliance action, not diagnostics: the
        // question "when did we stop serving this, and did we?" is asked months later by someone
        // answering a publisher.
        log.info(
                "retraction cascade: purged {} span(s) of document {} from source {} ({})",
                purged,
                event.documentId(),
                event.sourceId(),
                event.reason());
    }
}
