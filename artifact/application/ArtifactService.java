package com.atlas.artifact.application;

import com.atlas.artifact.api.ArtifactApi;
import com.atlas.artifact.api.ArtifactDetailView;
import com.atlas.artifact.api.ArtifactView;
import com.atlas.artifact.api.CellView;
import com.atlas.artifact.api.NewArtifact;
import com.atlas.artifact.api.NewCell;
import com.atlas.artifact.api.RevisionView;
import com.atlas.artifact.domain.model.Artifact;
import com.atlas.artifact.domain.model.ArtifactId;
import com.atlas.artifact.domain.model.Cell;
import com.atlas.artifact.domain.port.ArtifactRepository;
import com.atlas.shared.identity.TenantId;
import com.atlas.shared.provenance.AuthorityTier;
import com.atlas.shared.provenance.Citation;
import com.atlas.shared.provenance.SpanRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and reads deliverables on behalf of other modules. */
@Service
class ArtifactService implements ArtifactApi {

    /**
     * The system is the author of a machine-built deliverable.
     *
     * <p>Named rather than left null because the revision history is an audit trail, and "who
     * bound this cell" answered with a blank is the one answer that helps nobody. A later
     * human override overwrites neither this nor the value it attributes.
     */
    private static final String SYSTEM_AUTHOR = "system:research";

    private final ArtifactRepository artifacts;
    private final Clock clock;

    ArtifactService(ArtifactRepository artifacts, Clock clock) {
        this.artifacts = artifacts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ArtifactView create(NewArtifact command) {
        if (command.cells() == null || command.cells().isEmpty()) {
            throw new IllegalArgumentException(
                    ("run %s produced no cells to bind. An artifact with nothing in it cites "
                                    + "nothing, so every provenance rule holds vacuously and the "
                                    + "retraction sweep would never find it")
                            .formatted(command.runId()));
        }

        Instant now = clock.instant();
        Artifact artifact = new Artifact(
                ArtifactId.of(UUID.randomUUID().toString()),
                TenantId.of(command.tenantId()),
                command.runId(),
                command.title(),
                now);

        for (NewCell spec : command.cells()) {
            artifact.bind(toCell(spec), SYSTEM_AUTHOR, now);
        }

        artifacts.save(artifact);
        return view(artifact);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArtifactView> find(String artifactId) {
        return artifacts.findById(ArtifactId.of(artifactId)).map(ArtifactService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArtifactDetailView> detail(String artifactId) {
        return artifacts.findById(ArtifactId.of(artifactId)).map(ArtifactService::detail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactView> forRun(String runId) {
        return artifacts.byRun(runId).stream().map(ArtifactService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactView> recentFor(String tenantId, int limit) {
        return artifacts.recentFor(tenantId, limit).stream().map(ArtifactService::view).toList();
    }

    @Override
    @Transactional
    public ArtifactDetailView override(
            String artifactId, String cellRef, String value, String by, String rationale) {
        Artifact artifact = artifacts
                .findById(ArtifactId.of(artifactId))
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        artifact.override(cellRef, value, by, clock.instant(), rationale);
        artifacts.save(artifact);
        return detail(artifact);
    }

    @Override
    @Transactional
    public ArtifactView issue(String artifactId, String by) {
        Artifact artifact = artifacts
                .findById(ArtifactId.of(artifactId))
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));
        artifact.issue(by, clock.instant());
        artifacts.save(artifact);
        return view(artifact);
    }

    /**
     * Applies the domain's citation rules at the boundary.
     *
     * <p>A derived cell is recognised by carrying a formula, not by omitting evidence — otherwise
     * a caller that simply failed to send the span would be silently reclassified as having
     * computed the value, and an uncited number would enter a client document wearing the label
     * of one that did not need a citation.
     */
    private static Cell toCell(NewCell spec) {
        if (spec.isDerived()) {
            return Cell.derived(spec.cellRef(), spec.value(), spec.derivedFrom());
        }
        Citation evidence = new Citation(
                new SpanRef(
                        spec.spanId(),
                        spec.docId(),
                        spec.page(),
                        null,
                        spec.charStart(),
                        spec.charEnd()),
                spec.quotedText(),
                AuthorityTier.valueOf(spec.authority()),
                spec.publishedAt(),
                spec.sourceName());
        return Cell.extracted(spec.cellRef(), spec.value(), evidence);
    }

    private static ArtifactView view(Artifact artifact) {
        return new ArtifactView(
                artifact.id().value(),
                artifact.tenant().value().toString(),
                artifact.runId(),
                artifact.title(),
                artifact.status().name(),
                artifact.cells().size(),
                artifact.citedDocuments());
    }

    private static ArtifactDetailView detail(Artifact artifact) {
        return new ArtifactDetailView(
                artifact.id().value(),
                artifact.tenant().value().toString(),
                artifact.runId(),
                artifact.title(),
                artifact.status().name(),
                artifact.flagReason(),
                artifact.cells().stream().map(ArtifactService::cell).toList(),
                artifact.history().stream()
                        .map(revision -> new RevisionView(
                                revision.sequence(),
                                revision.kind().name(),
                                revision.cellRef(),
                                revision.detail(),
                                revision.by(),
                                revision.at()))
                        .toList(),
                artifact.citedDocuments());
    }

    private static CellView cell(Cell cell) {
        Citation evidence = cell.evidence();
        return new CellView(
                cell.cellRef(),
                cell.presentedValue(),
                cell.machineValue(),
                cell.overrideValue(),
                cell.overriddenBy(),
                cell.overriddenAt(),
                cell.overrideRationale(),
                cell.divergesFromSource(),
                cell.derivedFrom(),
                evidence == null ? null : evidence.span().docId(),
                evidence == null ? null : evidence.span().spanId(),
                evidence == null ? 0 : evidence.span().page(),
                evidence == null ? 0 : evidence.span().charStart(),
                evidence == null ? 0 : evidence.span().charEnd(),
                evidence == null ? null : evidence.quotedText(),
                evidence == null ? null : evidence.authority().name(),
                evidence == null ? null : evidence.publishedAt(),
                evidence == null ? null : evidence.sourceName());
    }
}
