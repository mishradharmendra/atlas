package com.atlas.artifact.domain.model;

import com.atlas.shared.identity.TenantId;
import com.atlas.shared.provenance.Citation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A deliverable: bound cells, an append-only history, and a life that ends when it is sent.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>An override is never overwritten by a refresh.</b> The one that keeps users. A
 *       correction destroyed by an overnight refresh is not a bug anyone forgives twice.
 *   <li><b>An issued artifact's cells are immutable.</b> You cannot un-send a deck, so a
 *       correction after issue is a new artifact, not an edit to this one. What an issued
 *       artifact still accepts is a <em>flag</em> — that is the honest response to ground moving
 *       underneath something already in a client's inbox.
 *   <li><b>Every cell carries a citation or a derivation.</b> Enforced by {@link Cell}.
 *   <li><b>History is append-only and densely sequenced.</b>
 * </ol>
 */
public class Artifact {

    private final ArtifactId id;
    private final TenantId tenant;
    private final String runId;
    private final String title;
    private final Instant createdAt;

    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private final List<Revision> history = new ArrayList<>();

    private ArtifactStatus status = ArtifactStatus.DRAFT;
    private Instant issuedAt;
    private String flagReason;

    public Artifact(ArtifactId id, TenantId tenant, String runId, String title, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "artifact id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.createdAt = Objects.requireNonNull(createdAt, "created-at");
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException(
                    "an artifact must name the run that produced it; without it the trace, the "
                            + "budget and the skill version behind a client document are all lost");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("an artifact must have a title");
        }
        this.runId = runId;
        this.title = title;
    }

    // -- building ----------------------------------------------------------

    public void bind(Cell cell, String by, Instant at) {
        requireDraft("bind a cell");
        cells.put(cell.cellRef(), cell);
        append(RevisionKind.CELL_BOUND, cell.cellRef(), cell.machineValue(), by, at);
    }

    /**
     * Records an analyst's correction.
     *
     * <p>Permitted on a draft only. After issue the document has been read by somebody, so
     * changing a number in place would mean two people hold the same artifact id and disagree
     * about what it says.
     */
    public void override(String cellRef, String value, String by, Instant at, String rationale) {
        requireDraft("override a cell");
        Cell cell = require(cellRef);
        cells.put(cellRef, cell.overriddenTo(value, by, at, rationale));
        append(RevisionKind.CELL_OVERRIDDEN, cellRef, "%s -> %s".formatted(cell.machineValue(), value), by, at);
    }

    /**
     * Recomputes a cell from its source, preserving any override.
     *
     * <p>The invariant this class exists for. {@link Cell#refreshedTo} keeps the correction; this
     * method's only job is to make sure nothing else in the aggregate throws it away.
     */
    public void refresh(String cellRef, String newMachineValue, Citation newEvidence, Instant at) {
        requireDraft("refresh a cell");
        Cell before = require(cellRef);
        Cell after = before.refreshedTo(newMachineValue, newEvidence);
        cells.put(cellRef, after);

        append(
                RevisionKind.CELL_REFRESHED,
                cellRef,
                after.divergesFromSource()
                        ? "source now %s, override stands at %s".formatted(newMachineValue, after.overrideValue())
                        : "%s -> %s".formatted(before.machineValue(), newMachineValue),
                "system",
                at);
    }

    // -- lifecycle ---------------------------------------------------------

    public void issue(String by, Instant at) {
        requireDraft("issue");
        if (cells.isEmpty()) {
            throw new IllegalStateException(
                    "artifact %s has no cells; an empty deliverable is a mistake, not a document"
                            .formatted(id));
        }
        this.status = ArtifactStatus.ISSUED;
        this.issuedAt = at;
        append(RevisionKind.ISSUED, null, "%d cell(s)".formatted(cells.size()), by, at);
    }

    /**
     * Marks that a source behind this deliverable has been withdrawn.
     *
     * <p>Works on an issued artifact, which is the only reason it exists. The alternative — doing
     * nothing because the document is immutable — leaves a client holding a figure the platform
     * knows it has lost the right to assert.
     *
     * <p>Idempotent: a replayed takedown must not stack duplicate flags on the same document.
     */
    public void flagRetractedSource(String docId, String reason, Instant at) {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("a retraction flag must name the document");
        }
        if (!citesDocument(docId)) {
            throw new IllegalArgumentException(
                    "artifact %s does not cite document %s".formatted(id, docId));
        }
        boolean alreadyFlagged = history.stream()
                .anyMatch(revision -> revision.kind() == RevisionKind.SOURCE_RETRACTED
                        && docId.equals(revision.cellRef()));
        if (alreadyFlagged) {
            return;
        }
        if (status == ArtifactStatus.ISSUED) {
            this.status = ArtifactStatus.FLAGGED;
        }
        this.flagReason = reason;
        append(RevisionKind.SOURCE_RETRACTED, docId, reason, "system", at);
    }

    public void withdraw(String by, String reason, Instant at) {
        if (status == ArtifactStatus.WITHDRAWN) {
            return;
        }
        this.status = ArtifactStatus.WITHDRAWN;
        this.flagReason = reason;
        append(RevisionKind.WITHDRAWN, null, reason, by, at);
    }

    // -- queries -----------------------------------------------------------

    /** Every document this deliverable rests on. What a retraction is matched against. */
    public List<String> citedDocuments() {
        return cells.values().stream()
                .map(Cell::sourceDocId)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    public boolean citesDocument(String docId) {
        return citedDocuments().contains(docId);
    }

    /** Cells where the analyst and the current source disagree. The refresh's whole output. */
    public List<Cell> divergentCells() {
        return cells.values().stream().filter(Cell::divergesFromSource).toList();
    }

    /**
     * What changed between two points in the history.
     *
     * <p>Returns the revisions rather than a value diff, because "the number went from 388 to 412"
     * is less useful than "the system refreshed it and an analyst had overridden it" — the second
     * says what to do next.
     */
    public List<Revision> changesBetween(int fromSequenceExclusive, int toSequenceInclusive) {
        return history.stream()
                .filter(revision -> revision.sequence() > fromSequenceExclusive
                        && revision.sequence() <= toSequenceInclusive)
                .toList();
    }

    private Cell require(String cellRef) {
        Cell cell = cells.get(cellRef);
        if (cell == null) {
            throw new IllegalArgumentException(
                    "artifact %s has no cell %s".formatted(id, cellRef));
        }
        return cell;
    }

    private void requireDraft(String action) {
        if (status != ArtifactStatus.DRAFT) {
            throw new IllegalStateException(
                    ("cannot %s on artifact %s: it is %s. A deliverable that has been sent cannot "
                                    + "be edited in place, because somebody is already holding the "
                                    + "version you would be changing")
                            .formatted(action, id, status));
        }
    }

    private void append(RevisionKind kind, String cellRef, String detail, String by, Instant at) {
        history.add(new Revision(history.size(), kind, cellRef, detail, by, at));
    }

    /** Restores persisted state without replaying the transitions that produced it. */
    public void rehydrate(
            ArtifactStatus status,
            Instant issuedAt,
            String flagReason,
            List<Cell> persistedCells,
            List<Revision> persistedHistory) {
        this.status = Objects.requireNonNull(status, "status");
        this.issuedAt = issuedAt;
        this.flagReason = flagReason;
        cells.clear();
        persistedCells.forEach(cell -> cells.put(cell.cellRef(), cell));
        history.clear();
        history.addAll(persistedHistory);
    }

    public ArtifactId id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public String runId() {
        return runId;
    }

    public String title() {
        return title;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ArtifactStatus status() {
        return status;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public String flagReason() {
        return flagReason;
    }

    public List<Cell> cells() {
        return List.copyOf(cells.values());
    }

    public Cell cell(String cellRef) {
        return require(cellRef);
    }

    public List<Revision> history() {
        return List.copyOf(history);
    }
}
