package com.atlas.knowledge.domain.model;

import com.atlas.knowledge.events.EdgeRetracted;
import com.atlas.knowledge.events.EdgeVerified;
import com.atlas.shared.provenance.Citation;
import com.atlas.shared.temporal.BitemporalAsOf;
import com.atlas.shared.temporal.Validity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A bitemporal, provenance-bearing, withheld-by-default edge between two entities.
 *
 * <h2>The two clocks</h2>
 *
 * <p>{@code validity} is world time: when the relationship held. {@code recordedAt} /
 * {@code supersededAt} is system time: when the platform believed it. A correction closes the
 * system-time window on the old row and opens one on the new, so the old answer remains
 * reconstructible. Overwriting in place would make every past answer unreproducible, and "why does
 * the system now say something different from the memo I sent in March?" unanswerable.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Born withheld.</b> There is no constructor that produces an {@link EdgeStatus#ACTIVE}
 *       edge and no setter for status. The sole path is {@link #verify}, which demands a
 *       {@link Verification} — which in turn cannot be built from one extractor or from two of the
 *       same family.
 *   <li><b>No edge without provenance.</b> An edge with no citation cannot be walked back to a
 *       sentence, cannot be shown to a user, and — decisively — cannot be found by the retraction
 *       cascade, so it would outlive the document it came from.
 *   <li><b>No self-loops.</b> "A supplies A" is always an extraction error, and it poisons
 *       traversal with a cycle of length one.
 *   <li><b>Terminal states are terminal.</b> A retracted edge cannot be re-verified; the right to
 *       assert it is gone regardless of whether it was true.
 * </ol>
 */
public class Relationship {

    private final RelationshipId id;
    private final EntityId subject;
    private final RelationshipType type;
    private final EntityId object;
    private final Validity validity;
    private final Instant recordedAt;
    private final List<Citation> provenance;
    private final ExtractorId extractedBy;

    private EdgeStatus status;
    private Verification verification;
    private Instant supersededAt;
    private String statusReason;

    private final List<Object> domainEvents = new ArrayList<>();

    public Relationship(
            RelationshipId id,
            EntityId subject,
            RelationshipType type,
            EntityId object,
            Validity validity,
            Instant recordedAt,
            ExtractorId extractedBy,
            List<Citation> provenance) {
        this.id = Objects.requireNonNull(id, "relationship id");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.type = Objects.requireNonNull(type, "relationship type");
        this.object = Objects.requireNonNull(object, "object");
        this.validity = Objects.requireNonNull(validity, "validity");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recorded-at");
        this.extractedBy = Objects.requireNonNull(extractedBy, "extractor");

        if (subject.equals(object)) {
            throw new IllegalArgumentException(
                    "an entity cannot relate to itself: " + subject + " " + type + " " + object);
        }
        if (provenance == null || provenance.isEmpty()) {
            throw new IllegalArgumentException(
                    "an edge must cite the text it was extracted from; without a citation it "
                            + "cannot be walked back, cannot be shown, and survives the retraction "
                            + "of its own source document");
        }
        this.provenance = List.copyOf(provenance);
        this.status = EdgeStatus.WITHHELD;
    }

    // -- lifecycle ---------------------------------------------------------

    /**
     * Promotes a withheld edge to active on the strength of two independent extractors.
     *
     * <p>The only transition into {@link EdgeStatus#ACTIVE} anywhere in the codebase.
     *
     * <p>Takes no separate instant. The {@link Verification} carries the moment the two extractors
     * agreed, and that <em>is</em> the moment the edge became visible; accepting a second instant
     * alongside it would let the two disagree, and system-time visibility would then depend on
     * whichever one this method happened to read.
     */
    public void verify(Verification corroboration) {
        Objects.requireNonNull(corroboration, "verification");
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "edge %s is %s, which is terminal; it cannot be verified"
                            .formatted(id, status));
        }
        if (status == EdgeStatus.ACTIVE) {
            return; // idempotent: a replayed verification must not raise a second event
        }
        this.status = EdgeStatus.ACTIVE;
        this.verification = corroboration;
        domainEvents.add(new EdgeVerified(
                id.value(),
                subject.value(),
                type.name(),
                object.value(),
                corroboration.first().toString(),
                corroboration.second().toString(),
                corroboration.at()));
    }

    /**
     * Records that review found the edge wrong.
     *
     * <p>Kept rather than deleted. A rejected edge is the per-source quality signal that tells you
     * which feed to stop paying for, and deleting it destroys the denominator.
     */
    public void reject(String reason, Instant at) {
        requireReason(reason);
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "edge %s is already %s".formatted(id, status));
        }
        this.status = EdgeStatus.REJECTED;
        this.statusReason = reason;
        this.supersededAt = at;
    }

    /**
     * Withdraws the edge because the document behind it was withdrawn.
     *
     * <p>Closes the system-time window rather than deleting the row: the edge was legitimately
     * believed until this instant, and an audit asking what the platform asserted last quarter
     * needs that to remain true.
     */
    public void retract(String reason, Instant at) {
        requireReason(reason);
        if (status == EdgeStatus.RETRACTED) {
            return; // idempotent: a replayed takedown must not raise a second event
        }
        this.status = EdgeStatus.RETRACTED;
        this.statusReason = reason;
        this.supersededAt = at;
        domainEvents.add(new EdgeRetracted(id.value(), subject.value(), object.value(), reason, at));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a status change must carry a reason");
        }
    }

    // -- queries -----------------------------------------------------------

    /**
     * Whether this edge was visible to a traversal standing at a given pair of instants.
     *
     * <p>Derived from the instants, not from the current {@code status}. That distinction is the
     * whole point of system time: an edge retracted last week was legitimately believed in March,
     * and a replay of a March answer has to see it. Reading the status field instead would make
     * every historical query return today's graph with today's corrections already applied, which
     * is precisely the unreproducibility the second clock exists to prevent.
     *
     * <p>Four conditions, each load-bearing: corroborated at all, true in the world then, already
     * corroborated by then, and not yet withdrawn by then. Note the third is measured from the
     * verification rather than from {@code recordedAt} — between being extracted and being
     * corroborated the edge was withheld, and a query landing in that window must not see it.
     */
    public boolean isVisibleAt(BitemporalAsOf at) {
        if (verification == null) {
            // Never corroborated, so never visible at any point in system time. Covers both a
            // still-withheld edge and one rejected before it was ever verified.
            return false;
        }
        if (!validity.contains(at.validAt())) {
            return false;
        }
        Instant known = at.knownAt().instant();
        if (known.isBefore(verification.at())) {
            return false;
        }
        return supersededAt == null || known.isBefore(supersededAt);
    }

    /**
     * Whether any of this edge's evidence came from the given document. Drives the cascade.
     *
     * <p>Any, not all: a single withdrawn source taints the edge. An edge supported by two
     * documents, one of which is withdrawn, is not two-thirds true — the platform has lost the
     * right to assert part of its basis, and re-establishing it is a fresh extraction.
     */
    public boolean derivedFrom(String documentId) {
        return provenance.stream().anyMatch(citation -> citation.span().docId().equals(documentId));
    }

    /** Restores persisted state without replaying the transitions that produced it. */
    public void rehydrate(
            EdgeStatus status, Verification verification, Instant supersededAt, String statusReason) {
        this.status = Objects.requireNonNull(status, "status");
        this.verification = verification;
        this.supersededAt = supersededAt;
        this.statusReason = statusReason;
    }

    public RelationshipId id() {
        return id;
    }

    public EntityId subject() {
        return subject;
    }

    public RelationshipType type() {
        return type;
    }

    public EntityId object() {
        return object;
    }

    public Validity validity() {
        return validity;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public Instant supersededAt() {
        return supersededAt;
    }

    public EdgeStatus status() {
        return status;
    }

    public Verification verification() {
        return verification;
    }

    public ExtractorId extractedBy() {
        return extractedBy;
    }

    public String statusReason() {
        return statusReason;
    }

    public List<Citation> provenance() {
        return provenance;
    }

    /** Drained by the application service after the transaction commits. */
    public List<Object> drainEvents() {
        var drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }
}
