package com.atlas.artifact.api;

import java.util.List;

/** What a caller must say to have a deliverable created: whose, from what run, and its content. */
public record NewArtifact(String tenantId, String runId, String title, List<NewCell> cells) {}
