package com.atlas.artifact.api;

import java.util.Optional;

/** The artifact module's inbound port. */
public interface ArtifactApi {

    /**
     * Creates a draft deliverable and returns it, identity included.
     *
     * <p>Fails rather than creating an empty artifact: a deliverable with no cells has nothing to
     * cite, so every provenance guarantee this module makes is vacuously true of it, and it would
     * sail through the retraction sweep by citing nothing.
     */
    ArtifactView create(NewArtifact command);

    Optional<ArtifactView> find(String artifactId);

    /** The full deliverable: cells, provenance and history. What a grid or a sheet renders. */
    Optional<ArtifactDetailView> detail(String artifactId);

    /** Deliverables produced by a run. The way a surface gets from a run to its output. */
    java.util.List<ArtifactView> forRun(String runId);

    /** A tenant's most recent deliverables, newest first. */
    java.util.List<ArtifactView> recentFor(String tenantId, int limit);

    /**
     * Records an analyst's correction to a cell.
     *
     * <p>The write-back path for a spreadsheet. The machine value is kept beside the correction
     * rather than replaced, so tomorrow's refresh can say the source now disagrees instead of
     * silently discarding the analyst's work.
     */
    ArtifactDetailView override(
            String artifactId, String cellRef, String value, String by, String rationale);

    /** Issues a draft. Issued artifacts are immutable in content. */
    ArtifactView issue(String artifactId, String by);
}
