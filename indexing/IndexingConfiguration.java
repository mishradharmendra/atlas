package com.atlas.indexing;

import com.atlas.indexing.domain.port.SearchIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IndexingConfiguration {

    /**
     * The fallback when no retrieval plane is configured. It refuses rather than doing nothing.
     *
     * <p>A no-op default would let the application start, accept a takedown, mark the cascade
     * complete and leave the content live — a compliance failure whose only symptom is silence.
     * Throwing leaves the event publication incomplete, so the registry retries it once the plane
     * is configured, and the failure is visible in the meantime.
     */
    @Bean
    @ConditionalOnMissingBean(SearchIndex.class)
    SearchIndex unconfiguredSearchIndex() {
        return documentId -> {
            throw new IllegalStateException(
                    ("cannot purge document %s: atlas.indexing.retrieval-url is not configured, "
                                    + "so the retraction cascade cannot reach the index. The "
                                    + "publication stays incomplete and will be retried.")
                            .formatted(documentId));
        };
    }
}
