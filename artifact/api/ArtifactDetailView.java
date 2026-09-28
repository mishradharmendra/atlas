package com.atlas.artifact.api;

import java.time.Instant;
import java.util.List;

/**
 * A deliverable with its contents and its history: what a workspace grid renders and what an
 * Excel add-in writes into a sheet.
 *
 * <p>Distinct from {@link ArtifactView}, which is the summary other modules pass around. This one
 * is a read model for a surface, and it carries the history because the question a client asks
 * about a number is almost always "has this changed, and who changed it".
 */
public record ArtifactDetailView(
        String artifactId,
        String tenantId,
        String runId,
        String title,
        String status,
        String flagReason,
        List<CellView> cells,
        List<RevisionView> history,
        List<String> citedDocuments) {}
