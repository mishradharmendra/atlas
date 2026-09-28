package com.atlas.entitlement.domain.model;

/** Whether a grant permits or forbids. */
public enum GrantEffect {

    ALLOW,

    /**
     * Forbids, unconditionally and regardless of any overlapping permission.
     *
     * <p>Carries information barriers and legal holds. If adding a permission could defeat one,
     * the barrier would be decorative, so {@link EntitlementSnapshot#decide} evaluates denials
     * first and returns on the first match.
     */
    DENY
}
