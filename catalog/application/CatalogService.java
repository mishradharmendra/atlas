package com.atlas.catalog.application;

import com.atlas.catalog.api.CatalogApi;
import com.atlas.catalog.api.DocumentView;
import com.atlas.catalog.api.RegisterDocumentCommand;
import com.atlas.catalog.api.RetractedDocumentException;
import com.atlas.catalog.api.UnlicensedSourceException;
import com.atlas.catalog.domain.model.DocType;
import com.atlas.catalog.domain.model.Document;
import com.atlas.catalog.domain.model.DocumentId;
import com.atlas.catalog.domain.port.DocumentRepository;
import com.atlas.ingestion.api.RightsApi;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The door into the corpus.
 *
 * <p>Every document that becomes searchable passes through {@link #register}, and that is the
 * point of having a single application service rather than letting the pipeline write catalog rows
 * directly: the licence check has exactly one place it can be forgotten, and it is here.
 *
 * <p>Events are published inside the transaction so the Modulith event-publication registry records
 * them atomically with the state change. A retraction that commits without its cascade being
 * durably recorded is a document that stays in the index after the takedown — the failure mode the
 * whole event mechanism exists to prevent.
 */
@Service
class CatalogService implements CatalogApi {

    private final DocumentRepository documents;
    private final RightsApi rights;
    private final ApplicationEventPublisher events;

    CatalogService(
            DocumentRepository documents, RightsApi rights, ApplicationEventPublisher events) {
        this.documents = documents;
        this.rights = rights;
        this.events = events;
    }

    @Override
    @Transactional
    public DocumentView register(RegisterDocumentCommand command, Instant at) {
        // Idempotency before authorisation: a replayed feed must not be refused merely because
        // the contract lapsed after the document was lawfully admitted.
        var existing = documents.findByContentHash(command.contentHash());
        if (existing.isPresent()) {
            // Unless it was withdrawn. Returning a retracted document here reads as success to
            // every caller that only checks for an exception, so a routine re-ingest put its
            // spans back in the index while the catalog still said RETRACTED — the withdrawal
            // looked applied and was not. Reinstatement has to be a deliberate act, not the
            // side effect of replaying a feed.
            if (!existing.get().isCitable()) {
                throw new RetractedDocumentException(
                        ("document '%s' was retracted (%s) and cannot be re-registered. A "
                                        + "withdrawal is a commercial act; undoing it is another "
                                        + "one, and neither happens by replaying an ingest.")
                                .formatted(
                                        existing.get().id().value(),
                                        existing.get().retractionReason()));
            }
            return toView(existing.get());
        }

        if (!rights.mayBeCatalogued(command.sourceId(), at)) {
            throw new UnlicensedSourceException(
                    ("source '%s' may not be catalogued at %s: no contract permits lexical "
                                    + "indexing, or an embargo has not expired. The bytes remain "
                                    + "in the landing zone and can be admitted once a contract "
                                    + "exists.")
                            .formatted(command.sourceId(), at));
        }

        Document document = new Document(
                DocumentId.of(command.documentId()),
                command.sourceId(),
                DocType.valueOf(command.docType()),
                command.publishedAt(),
                command.contentHash(),
                command.storageKey());

        documents.save(document);
        publish(document);
        return toView(document);
    }

    @Override
    @Transactional
    public DocumentView supersede(String documentId, String successorId, Instant at) {
        Document document = load(documentId);
        document.supersededBy(DocumentId.of(successorId), at);
        documents.save(document);
        publish(document);
        return toView(document);
    }

    @Override
    @Transactional
    public DocumentView retract(String documentId, String reason, Instant at) {
        Document document = load(documentId);
        document.retract(reason, at);
        documents.save(document);
        publish(document);
        return toView(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentView find(String documentId) {
        return toView(load(documentId));
    }

    private Document load(String documentId) {
        return documents
                .findById(DocumentId.of(documentId))
                .orElseThrow(() -> new NoSuchElementException("no document " + documentId));
    }

    private void publish(Document document) {
        document.drainEvents().forEach(events::publishEvent);
    }

    private static DocumentView toView(Document document) {
        return new DocumentView(
                document.id().value(),
                document.sourceId(),
                document.docType().name(),
                document.docType().authority().name(),
                document.publishedAt(),
                document.contentHash(),
                document.storageKey(),
                document.status().name(),
                document.supersededBy() == null ? null : document.supersededBy().value(),
                document.retractionReason());
    }
}
