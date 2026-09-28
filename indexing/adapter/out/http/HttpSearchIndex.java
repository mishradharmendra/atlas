package com.atlas.indexing.adapter.out.http;

import com.atlas.indexing.domain.port.SearchIndex;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Driven adapter: asks the Python retrieval plane to purge a document.
 *
 * <p>The JVM does not talk to OpenSearch. Index topology, analyzers, the vector space and the
 * filter semantics all belong to the plane that owns retrieval, and a second writer with its own
 * idea of the mapping is how two systems end up disagreeing about what is in the index.
 */
@Component
@ConditionalOnProperty("atlas.indexing.retrieval-url")
class HttpSearchIndex implements SearchIndex {

    /**
     * A purge runs inside an asynchronous module listener, so an unbounded call holds that
     * thread until the socket gives up. Ten seconds is long for a delete-by-query and short
     * enough that a wedged retrieval plane surfaces as a failed publication to retry rather
     * than as a listener that never returns.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;

    /**
     * Builds its own client rather than injecting {@code RestClient.Builder}.
     *
     * <p>It injected the builder until 2026-09-23, and no such bean exists in this build: the
     * first attempt to switch the cascade on failed at startup with "required a bean of type
     * RestClient$Builder". The property had never been set, so an adapter that could not be
     * constructed sat behind it looking like a working feature.
     */
    HttpSearchIndex(
            @org.springframework.beans.factory.annotation.Value("${atlas.indexing.retrieval-url}")
                    String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public int purgeDocument(String documentId) {
        PurgeResponse response = client.post()
                .uri("/internal/v1/index/retractions")
                .body(new PurgeRequest(documentId))
                .retrieve()
                .body(PurgeResponse.class);

        // A null body means the call returned without saying what it did. Treating that as
        // success would mark the cascade complete on no evidence.
        if (response == null) {
            throw new IllegalStateException(
                    "retrieval plane returned no body for the purge of " + documentId);
        }
        return response.purged();
    }

    record PurgeRequest(String documentId) {}

    record PurgeResponse(int purged) {}
}
