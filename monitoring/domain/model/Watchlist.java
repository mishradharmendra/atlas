package com.atlas.monitoring.domain.model;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.identity.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** What one person asked to be told about. */
public class Watchlist {

    private final WatchlistId id;
    private final TenantId tenant;
    private final PrincipalId owner;
    private final String name;
    private final Set<String> subjectIds;
    private final Instant createdAt;

    private MaterialityRule rule;
    private boolean active;

    public Watchlist(
            WatchlistId id,
            TenantId tenant,
            PrincipalId owner,
            String name,
            Set<String> subjectIds,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id, "watchlist id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.createdAt = Objects.requireNonNull(createdAt, "created-at");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a watchlist must have a name");
        }

        // A watchlist watching nothing still matches nothing, so it is harmless and useless —
        // and it reads as configured on every screen that lists it, which is how someone comes
        // to believe they are covered.
        if (subjectIds == null || subjectIds.isEmpty()) {
            throw new IllegalArgumentException(
                    ("watchlist '%s' watches no subjects. It would sit in the list looking "
                                    + "configured while matching nothing")
                            .formatted(name));
        }

        this.name = name;
        this.subjectIds = Set.copyOf(subjectIds);
    }

    /** Sets what this list considers worth an interruption. */
    public void applyRule(MaterialityRule materialityRule) {
        this.rule = Objects.requireNonNull(materialityRule, "materiality rule");
    }

    /**
     * Starts monitoring. Refuses without a materiality rule.
     *
     * <p>Defaulting to "send everything" is the decision that kills the feature: the first day is
     * a firehose, the second is a mute, and a muted watchlist is indistinguishable from a working
     * one right up until something is missed.
     */
    public void activate() {
        if (rule == null) {
            throw new IllegalStateException(
                    ("watchlist '%s' has no materiality rule. Activating it would subscribe its "
                                    + "owner to every change, which is the configuration people "
                                    + "mute rather than tune")
                            .formatted(name));
        }
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Whether a change to this subject, of this kind, is one this list asked for. */
    public boolean wants(String subjectId, String relationshipType) {
        return active && subjectIds.contains(subjectId) && rule.covers(relationshipType);
    }

    public WatchlistId id() {
        return id;
    }

    public TenantId tenant() {
        return tenant;
    }

    public PrincipalId owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Set<String> subjectIds() {
        return subjectIds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public MaterialityRule rule() {
        return rule;
    }

    public boolean isActive() {
        return active;
    }

    /** Reconstitutes from storage. */
    public void rehydrate(MaterialityRule storedRule, boolean storedActive) {
        this.rule = storedRule;
        this.active = storedActive;
    }
}
