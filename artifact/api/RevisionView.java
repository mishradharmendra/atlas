package com.atlas.artifact.api;

import java.time.Instant;

/** One entry in a deliverable's append-only history. */
public record RevisionView(
        int sequence, String kind, String cellRef, String detail, String by, Instant at) {}
