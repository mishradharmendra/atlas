package com.atlas.artifact.adapter.out.persistence;

import com.atlas.artifact.domain.model.Artifact;
import com.atlas.artifact.domain.model.ArtifactId;
import com.atlas.artifact.domain.model.ArtifactStatus;
import com.atlas.artifact.domain.model.Cell;
import com.atlas.artifact.domain.model.Revision;
import com.atlas.artifact.domain.model.RevisionKind;
import com.atlas.artifact.domain.port.ArtifactRepository;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.provenance.AuthorityTier;
import com.atlas.shared.provenance.BoundingBox;
import com.atlas.shared.provenance.Citation;
import com.atlas.shared.provenance.SpanRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface ArtifactJpaRepository extends JpaRepository<ArtifactRow, String> {

    /** The cascade's query: every deliverable with a cell citing this document. */
    @Query("""
            select a from ArtifactRow a
            where a.artifactId in (select c.artifactId from CellRow c where c.docId = :docId)
            """)
    List<ArtifactRow> findCitingDocument(@Param("docId") String docId);

    List<ArtifactRow> findByRunIdOrderByCreatedAtDesc(String runId);

    List<ArtifactRow> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable page);
}

interface CellJpaRepository extends JpaRepository<CellRow, CellRow.Key> {
    List<CellRow> findByArtifactId(String artifactId);
}

interface RevisionJpaRepository extends JpaRepository<RevisionRow, RevisionRow.Key> {
    List<RevisionRow> findByArtifactIdOrderBySequenceAsc(String artifactId);
}

/**
 * Driven adapter for deliverables.
 *
 * <p>Revisions are written append-only: an entry already persisted is never rewritten. A history
 * that can be rewritten cannot answer "what did we send them in March?", which is the only
 * question it exists for.
 */
@Repository
class JpaArtifactRepository implements ArtifactRepository {

    private final ArtifactJpaRepository artifacts;
    private final CellJpaRepository cells;
    private final RevisionJpaRepository revisions;

    JpaArtifactRepository(
            ArtifactJpaRepository artifacts,
            CellJpaRepository cells,
            RevisionJpaRepository revisions) {
        this.artifacts = artifacts;
        this.cells = cells;
        this.revisions = revisions;
    }

    @Override
    public Optional<Artifact> findById(ArtifactId id) {
        return artifacts.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Artifact> citingDocument(String docId) {
        return artifacts.findCitingDocument(docId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Artifact> byRun(String runId) {
        return artifacts.findByRunIdOrderByCreatedAtDesc(runId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Artifact> recentFor(String tenantId, int limit) {
        return artifacts
                .findByTenantIdOrderByCreatedAtDesc(UUID.fromString(tenantId), PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(Artifact artifact) {
        ArtifactRow row = artifacts.findById(artifact.id().value()).orElseGet(ArtifactRow::new);
        row.artifactId = artifact.id().value();
        row.tenantId = artifact.tenant().value();
        row.runId = artifact.runId();
        row.title = artifact.title();
        row.createdAt = artifact.createdAt();
        row.status = artifact.status().name();
        row.issuedAt = artifact.issuedAt();
        row.flagReason = artifact.flagReason();
        artifacts.save(row);

        artifact.cells().forEach(cell -> cells.save(toRow(artifact.id().value(), cell)));

        int persisted = revisions.findByArtifactIdOrderBySequenceAsc(artifact.id().value()).size();
        for (Revision revision : artifact.history()) {
            if (revision.sequence() < persisted) {
                continue; // append-only: an entry already written is never rewritten
            }
            revisions.save(toRow(artifact.id().value(), revision));
        }
    }

    private static CellRow toRow(String artifactId, Cell cell) {
        CellRow row = new CellRow();
        row.artifactId = artifactId;
        row.cellRef = cell.cellRef();
        row.machineValue = cell.machineValue();
        row.derivedFrom = cell.derivedFrom();
        row.overrideValue = cell.overrideValue();
        row.overriddenBy = cell.overriddenBy();
        row.overriddenAt = cell.overriddenAt();
        row.overrideRationale = cell.overrideRationale();
        row.machineValueWhenOverridden = cell.machineValueWhenOverridden();

        Citation evidence = cell.evidence();
        if (evidence != null) {
            row.spanId = evidence.span().spanId();
            row.docId = evidence.span().docId();
            row.page = evidence.span().page();
            row.charStart = evidence.span().charStart();
            row.charEnd = evidence.span().charEnd();
            row.quotedText = evidence.quotedText();
            row.authority = evidence.authority().name();
            row.publishedAt = evidence.publishedAt();
            row.sourceName = evidence.sourceName();
        }
        return row;
    }

    private static RevisionRow toRow(String artifactId, Revision revision) {
        RevisionRow row = new RevisionRow();
        row.artifactId = artifactId;
        row.sequence = revision.sequence();
        row.kind = revision.kind().name();
        row.cellRef = revision.cellRef();
        row.detail = revision.detail();
        row.madeBy = revision.by();
        row.madeAt = revision.at();
        return row;
    }

    private Artifact toDomain(ArtifactRow row) {
        Artifact artifact = new Artifact(
                ArtifactId.of(row.artifactId),
                new TenantId(row.tenantId),
                row.runId,
                row.title,
                row.createdAt);

        artifact.rehydrate(
                ArtifactStatus.valueOf(row.status),
                row.issuedAt,
                row.flagReason,
                cells.findByArtifactId(row.artifactId).stream()
                        .map(JpaArtifactRepository::toCell)
                        .toList(),
                revisions.findByArtifactIdOrderBySequenceAsc(row.artifactId).stream()
                        .map(JpaArtifactRepository::toRevision)
                        .toList());
        return artifact;
    }

    private static Cell toCell(CellRow row) {
        Citation evidence = row.docId == null
                ? null
                : new Citation(
                        new SpanRef(
                                row.spanId,
                                row.docId,
                                row.page == null ? 0 : row.page,
                                BoundingBox.none(),
                                row.charStart == null ? 0 : row.charStart,
                                row.charEnd == null ? 0 : row.charEnd),
                        row.quotedText,
                        AuthorityTier.valueOf(row.authority),
                        row.publishedAt,
                        row.sourceName);

        return new Cell(
                row.cellRef,
                row.machineValue,
                evidence,
                row.derivedFrom,
                row.overrideValue,
                row.overriddenBy,
                row.overriddenAt,
                row.overrideRationale,
                row.machineValueWhenOverridden);
    }

    private static Revision toRevision(RevisionRow row) {
        return new Revision(
                row.sequence,
                RevisionKind.valueOf(row.kind),
                row.cellRef,
                row.detail,
                row.madeBy,
                row.madeAt);
    }
}
