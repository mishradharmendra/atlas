package com.atlas.catalog.domain.model;

import com.atlas.catalog.events.DocumentRetracted;
import com.atlas.catalog.events.DocumentSuperseded;
import com.atlas.shared.temporal.AsOf;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A document in the corpus: its identity, classification, lifecycle and where its bytes live.
 *
 * <p>Holds no content. Content is in the landing zone, addressed by hash; a catalog row that
 * duplicated it would start drifting the first time a re-parse changed something.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Retraction is terminal.</b> A withdrawn document cannot be restored through this
 *       aggregate. Restoring it would leave derived artefacts that were purged during the cascade
 *       silently missing, so a reinstatement is a fresh acquisition with a fresh identity.
 *   <li><b>Supersession names its successor.</b> "Out of date" without a pointer to what replaced
 *       it is not actionable by an agent and not explicable to a user.
 *   <li><b>A document cannot supersede itself</b>, directly or as a self-loop.
 *   <li><b>Publication date is immutable.</b> Every temporal ranking decision and every rubric's
 *       currency judgement rests on it.
 * </ol>
 *
 * <p>Lifecycle changes raise domain events rather than performing the cascade inline. The catalog
 * knows a document was retracted; it has no business knowing which indexes exist.
 */
public class Document {

    private final DocumentId id;
    private final String sourceId;
    private final DocType docType;
    private final Instant publishedAt;
    private final String contentHash;
    private final String storageKey;

    private DocumentStatus status;
    private DocumentId supersededBy;
    private String retractionReason;

    private final List<Object> domainEvents = new ArrayList<>();

    public Document(
            DocumentId id,
            String sourceId,
            DocType docType,
            Instant publishedAt,
            String contentHash,
            String storageKey) {
        this.id = Objects.requireNonNull(id, "document id");
        this.sourceId = requireText(sourceId, "source id");
        this.docType = Objects.requireNonNull(docType, "doc type");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publication date");
        this.contentHash = requireText(contentHash, "content hash");
        this.storageKey = requireText(storageKey, "storage key");
        this.status = DocumentStatus.PUBLISHED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    // -- lifecycle ---------------------------------------------------------

    /**
     * Records that a later document replaces this one.
     *
     * <p>The document stays searchable. Point-in-time questions legitimately need what was reported
     * at the time, and the fact of a restatement is itself a signal — surfacing it as a conflict is
     * more useful than hiding the original.
     */
    public void supersededBy(DocumentId successor, Instant at) {
        Objects.requireNonNull(successor, "successor");
        if (successor.equals(id)) {
            throw new IllegalArgumentException("a document cannot supersede itself: " + id);
        }
        if (status == DocumentStatus.RETRACTED) {
            throw new IllegalStateException(
                    "document %s is retracted and cannot be superseded; a retraction is terminal"
                            .formatted(id));
        }
        this.status = DocumentStatus.SUPERSEDED;
        this.supersededBy = successor;
        domainEvents.add(new DocumentSuperseded(id.value(), successor.value(), at));
    }

    /**
     * Withdraws the document and raises the event that drives the cascade.
     *
     * <p>The cascade — indexes, derived graph edges, cached memory, flags on deliverables that
     * cited it — is the subscribers' work. Doing it here would couple the catalog to every store in
     * the platform.
     */
    public void retract(String reason, Instant at) {
        requireText(reason, "retraction reason");
        if (status == DocumentStatus.RETRACTED) {
            return; // idempotent: a takedown replayed must not raise a second cascade
        }
        this.status = DocumentStatus.RETRACTED;
        this.retractionReason = reason;
        domainEvents.add(new DocumentRetracted(id.value(), sourceId, reason, at));
    }

    // -- queries -----------------------------------------------------------

    /**
     * Restores persisted state without replaying the transitions that produced it.
     *
     * <p>Reconstitution is not a lifecycle event. Rehydrating a retracted document by calling
     * {@link #retract} would raise the cascade again on every read, so loading state and changing
     * state are deliberately different operations and only the second raises anything.
     */
    public void rehydrate(DocumentStatus status, DocumentId supersededBy, String retractionReason) {
        this.status = Objects.requireNonNull(status, "status");
        this.supersededBy = supersededBy;
        this.retractionReason = retractionReason;
    }

    /**
     * Whether this document may still be returned as evidence.
     *
     * <p>Superseded content still qualifies; retracted content never does.
     */
    public boolean isCitable() {
        return status != DocumentStatus.RETRACTED;
    }

    /**
     * Whether the document is still current at the given point in time, judged against its own
     * type's shelf life rather than a single global window.
     */
    public boolean isCurrentAt(AsOf at) {
        if (status != DocumentStatus.PUBLISHED) {
            return false;
        }
        if (at.instant().isBefore(publishedAt)) {
            return false;
        }
        return docType.isCurrentAfter(Duration.between(publishedAt, at.instant()));
    }

    /** Documents published after the given cutoff carry information the market has not digested. */
    public boolean landedAfter(Instant cutoff) {
        return publishedAt.isAfter(cutoff);
    }

    public DocumentId id() {
        return id;
    }

    public String sourceId() {
        return sourceId;
    }

    public DocType docType() {
        return docType;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public String contentHash() {
        return contentHash;
    }

    public String storageKey() {
        return storageKey;
    }

    public DocumentStatus status() {
        return status;
    }

    public DocumentId supersededBy() {
        return supersededBy;
    }

    public String retractionReason() {
        return retractionReason;
    }

    /** Drained by the application service after the transaction commits. */
    public List<Object> drainEvents() {
        var drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }
}
