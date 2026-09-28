package com.atlas.shared.outcome;

/**
 * A first-class record of something the system declined to assert.
 *
 * <h2>Why abstention is modelled rather than logged</h2>
 *
 * <p>In a multi-step agent, per-step accuracy compounds: 0.95<sup>10</sup> ≈ 0.60. Reaching 95%
 * end-to-end over ten steps would require 99.5% per step, which is not achievable. The productive
 * response is not to chase per-step accuracy but to change the <em>shape</em> of the error.
 *
 * <p>A system that is 90% right and 10% "I could not establish this" is deployable. A system that
 * is 97% right and 3% confidently wrong is not, because the 3% is indistinguishable from the 97%
 * at the point of use and propagates into a committee deck with a vendor's name attached.
 *
 * <p>So abstentions are persisted, counted, reported next to quality, and gated on. A run whose
 * abstention rate jumps is telling you something a quality average would hide.
 */
public record Abstention(String stepId, AbstentionReason reason, String detail, float coverageEstimate) {

    public Abstention {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("abstention must name the step that abstained");
        }
        if (reason == null) {
            throw new IllegalArgumentException("abstention must carry a reason");
        }
        if (coverageEstimate < 0f || coverageEstimate > 1f) {
            throw new IllegalArgumentException(
                    "coverage estimate must be in [0,1], was " + coverageEstimate);
        }
    }

    /**
     * Whether this abstention reflects the corpus rather than the system.
     *
     * <p>The distinction drives what the user is told. "No such document exists" is a correct,
     * final answer worth surfacing confidently. "I ran out of budget" is an operational failure
     * wearing the same clothes, and must never be presented as the former.
     */
    public boolean isGroundTruth() {
        return reason == AbstentionReason.NO_EVIDENCE_EXISTS;
    }
}
