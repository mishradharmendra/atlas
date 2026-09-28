package com.atlas.catalog.adapter.out.persistence;

import com.atlas.catalog.domain.model.DocType;
import com.atlas.catalog.domain.model.Document;
import com.atlas.catalog.domain.model.DocumentId;
import com.atlas.catalog.domain.model.DocumentStatus;
import com.atlas.catalog.domain.port.DocumentRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface DocumentJpaRepository extends JpaRepository<DocumentRow, String> {
    Optional<DocumentRow> findByContentHash(String contentHash);
}

/**
 * Driven adapter for the catalog.
 *
 * <p>Rehydrating a {@link Document} has to reach past its public lifecycle methods: calling
 * {@code retract} to restore a retracted document would raise the cascade event a second time on
 * every read. Loading state and applying state transitions are different operations, and the
 * aggregate is right to only offer the second.
 */
@Repository
class JpaDocumentRepository implements DocumentRepository {

    private final DocumentJpaRepository rows;

    JpaDocumentRepository(DocumentJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Document> findById(DocumentId id) {
        return rows.findById(id.value()).map(JpaDocumentRepository::toDomain);
    }

    @Override
    public Optional<Document> findByContentHash(String contentHash) {
        return rows.findByContentHash(contentHash).map(JpaDocumentRepository::toDomain);
    }

    @Override
    public void save(Document document) {
        DocumentRow row = rows.findById(document.id().value()).orElseGet(DocumentRow::new);
        row.documentId = document.id().value();
        row.sourceId = document.sourceId();
        row.docType = document.docType().name();
        row.publishedAt = document.publishedAt();
        row.contentHash = document.contentHash();
        row.storageKey = document.storageKey();
        row.status = document.status().name();
        row.supersededBy =
                document.supersededBy() == null ? null : document.supersededBy().value();
        row.retractionReason = document.retractionReason();
        rows.save(row);
    }

    private static Document toDomain(DocumentRow row) {
        Document document = new Document(
                DocumentId.of(row.documentId),
                row.sourceId,
                DocType.valueOf(row.docType),
                row.publishedAt,
                row.contentHash,
                row.storageKey);

        document.rehydrate(
                DocumentStatus.valueOf(row.status),
                row.supersededBy == null ? null : DocumentId.of(row.supersededBy),
                row.retractionReason);
        return document;
    }
}
