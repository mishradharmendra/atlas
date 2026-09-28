package com.atlas.knowledge.domain.model;

import com.atlas.shared.temporal.AsOf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An entity, and every name it has been known by over time.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>One entity cannot hold the same name twice over overlapping windows.</b> Two records of
 *       "Facebook" for this entity covering 2010–2021 and 2015–2030 are not a conflict to resolve,
 *       they are corrupt data: they double-count in any scoring that weighs how many sources use a
 *       name, and they make the resolver's answer depend on iteration order.
 *   <li><b>Kind and canonical name are immutable.</b> A rename is a new alias, not an edit. Editing
 *       the canonical name destroys the evidence that the old name ever applied, which is the one
 *       thing a historical corpus needs it for.
 * </ol>
 *
 * <p>Deliberately <em>not</em> an invariant: that a name maps to only one entity. "Apple" is a
 * computer manufacturer and a record label, and both are correct. Cross-entity ambiguity is real
 * and is the resolver's job to abstain on — see {@code EntityResolver}. Forbidding it here would
 * force whoever hits it to pick one arbitrarily, which converts a detectable ambiguity into a
 * silent error.
 */
public class CanonicalEntity {

    private final EntityId id;
    private final EntityKind kind;
    private final String canonicalName;
    private final List<Alias> aliases = new ArrayList<>();

    public CanonicalEntity(EntityId id, EntityKind kind, String canonicalName) {
        this.id = Objects.requireNonNull(id, "entity id");
        this.kind = Objects.requireNonNull(kind, "entity kind");
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonical name must not be blank");
        }
        this.canonicalName = canonicalName;
    }

    /**
     * Records a name this entity was known by.
     *
     * <p>Rejects a duplicate of a name already held over an overlapping window. Abutting windows
     * are fine — that is a name being dropped and later readopted, which happens.
     */
    public void addAlias(Alias alias) {
        Objects.requireNonNull(alias, "alias");
        for (Alias existing : aliases) {
            if (existing.normalised().equals(alias.normalised())
                    && existing.validity().overlaps(alias.validity())) {
                throw new IllegalArgumentException(
                        "entity %s already holds alias '%s' over %s, which overlaps %s"
                                .formatted(id, alias.value(), existing.validity(), alias.validity()));
            }
        }
        aliases.add(alias);
    }

    /** The names this entity went by at a point in time. */
    public List<Alias> aliasesAt(AsOf at) {
        return aliases.stream().filter(alias -> alias.appliesAt(at)).toList();
    }

    /**
     * Whether the entity answered to this name at that point in time.
     *
     * <p>The canonical name is matched too, on the same normalisation, so an entity is always
     * findable by the name it is filed under.
     */
    public boolean wasKnownAs(String name, AsOf at) {
        String probe = Alias.normalise(name);
        if (Alias.normalise(canonicalName).equals(probe)) {
            return true;
        }
        return aliases.stream()
                .anyMatch(alias -> alias.normalised().equals(probe) && alias.appliesAt(at));
    }

    public Optional<Alias> aliasNamed(String name, AsOf at) {
        String probe = Alias.normalise(name);
        return aliases.stream()
                .filter(alias -> alias.normalised().equals(probe) && alias.appliesAt(at))
                .findFirst();
    }

    /**
     * Restores persisted aliases, re-checking the overlap invariant on the way in.
     *
     * <p>The re-check is not redundant. Rows can arrive from a backfill, a restored dump or a
     * hand-written correction, none of which passed through {@link #addAlias}, and an aggregate
     * that trusts its own store cannot detect the one case where the store was wrong.
     */
    public void rehydrate(List<Alias> persisted) {
        aliases.clear();
        persisted.forEach(this::addAlias);
    }

    public EntityId id() {
        return id;
    }

    public EntityKind kind() {
        return kind;
    }

    public String canonicalName() {
        return canonicalName;
    }

    public List<Alias> aliases() {
        return List.copyOf(aliases);
    }
}
