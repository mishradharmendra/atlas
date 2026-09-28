package com.atlas.monitoring.domain.model;

/** Why a trigger was not delivered. Always recorded; never a reason to discard the trigger. */
public enum SuppressionReason {

    /** The same real-world fact has already been delivered on this watchlist. */
    DUPLICATE,

    /** The same subject and kind of change alerted recently. Repetition is not news. */
    QUIET_PERIOD,

    /**
     * The watchlist's rate cap was reached.
     *
     * <p>Collapsed into a digest rather than dropped. During a flood the one that mattered is
     * exactly as likely to be discarded as the rest, and a flood is when something usually does
     * matter.
     */
    FANNED_OUT,

    /** The change was not of a kind this watchlist asked about. */
    IMMATERIAL
}
