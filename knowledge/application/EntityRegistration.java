package com.atlas.knowledge.application;

import com.atlas.knowledge.domain.model.Alias;
import com.atlas.knowledge.domain.model.CanonicalEntity;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.EntityKind;
import com.atlas.knowledge.domain.port.EntityRepository;
import com.atlas.shared.temporal.Validity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the entities a question can name.
 *
 * <p>{@code EntityRepository.save} existed on the port and nothing in production called it, so
 * {@code knowledge_entity} stayed empty and every resolution abstained. A resolver that never
 * resolves is indistinguishable from one enforcing its ambiguity rule correctly, which is why
 * this survived: the failure looks exactly like the safe behaviour.
 *
 * <p>Registration is idempotent on the entity id. Ingest runs repeatedly over the same issuers
 * and a second run must not create a second Amazon — two entities sharing a name is precisely
 * the ambiguity the resolver refuses to guess through, so a duplicate would silently disable
 * resolution for that issuer.
 */
@Service
public class EntityRegistration {

    private final EntityRepository entities;
    private final Clock clock;

    EntityRegistration(EntityRepository entities, Clock clock) {
        this.entities = entities;
        this.clock = clock;
    }

    @Transactional
    public String register(
            String entityId,
            EntityKind kind,
            String canonicalName,
            List<String> aliases,
            String source,
            Instant knownFrom) {
        EntityId id = EntityId.of(entityId);
        CanonicalEntity entity =
                entities.findById(id).orElseGet(() -> new CanonicalEntity(id, kind, canonicalName));

        // Dated from when the name was in use, not from when we happened to load it. Stamping
        // `now` makes every alias invisible to any question asked as of an earlier moment,
        // which is every point-in-time question -- and the resolver then abstains with
        // NO_EVIDENCE_EXISTS, indistinguishable from a name it has genuinely never seen.
        Validity since = Validity.openFrom(knownFrom);
        for (String alias : aliases) {
            if (alias == null || alias.isBlank() || alreadyHeld(entity, alias, since)) {
                continue;
            }
            entity.addAlias(new Alias(alias, since, source));
        }

        entities.save(entity);
        return entity.id().value();
    }

    /** Everything a question may name here. */
    @Transactional(readOnly = true)
    public List<CanonicalEntity> coverage() {
        return entities.all();
    }

    /**
     * The same question {@code addAlias} asks, so this skips rather than throws.
     *
     * <p>Asking instead whether the entity {@code wasKnownAs} the name *at* {@code knownFrom}
     * is a different question with the same shape, and it answers no for a name recorded from a
     * later date. Registering a company's April filing after its August one then threw, because
     * the alias was not yet in force in April but its open-ended window overlaps April all the
     * same.
     */
    private static boolean alreadyHeld(CanonicalEntity entity, String alias, Validity window) {
        String normalised = Alias.normalise(alias);
        return entity.aliases().stream()
                .anyMatch(held ->
                        held.normalised().equals(normalised) && held.validity().overlaps(window));
    }
}
