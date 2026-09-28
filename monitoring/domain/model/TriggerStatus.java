package com.atlas.monitoring.domain.model;

/** Where a trigger got to. */
public enum TriggerStatus {

    /** Raised and owed to the watcher. */
    PENDING,

    /** Sent. */
    DELIVERED,

    /** Deliberately not sent, with a reason recorded beside it. */
    SUPPRESSED
}
