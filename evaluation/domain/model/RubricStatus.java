package com.atlas.evaluation.domain.model;

public enum RubricStatus {

    /** Being authored. Criteria may be added, removed and reweighted. */
    DRAFT,

    /** Closed. Revising means a new version, never an edit. */
    FROZEN
}
