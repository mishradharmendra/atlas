package com.atlas.artifact.api;

import java.util.List;

/**
 * A deliverable as other modules see it.
 *
 * <p>Carries {@code citedDocuments} because that is what makes a retraction actionable from the
 * outside: the question asked of this module in an incident is "which documents is this standing
 * on", and answering it should not require handing out the aggregate.
 */
public record ArtifactView(
        String artifactId,
        String tenantId,
        String runId,
        String title,
        String status,
        int cellCount,
        List<String> citedDocuments) {}
