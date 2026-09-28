package com.atlas.knowledge.application;

import com.atlas.knowledge.api.Resolution;
import com.atlas.knowledge.domain.model.Alias;
import com.atlas.knowledge.domain.model.CanonicalEntity;
import com.atlas.knowledge.domain.port.EntityRepository;
import com.atlas.shared.outcome.Abstention;
import com.atlas.shared.outcome.AbstentionReason;
import com.atlas.shared.temporal.AsOf;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Turns a name in text into an entity, or declines.
 *
 * <h2>Why this abstains instead of guessing</h2>
 *
 * <p>Entity resolution is where a knowledge graph quietly becomes wrong. Every mention has to be
 * attached to something, the code that attaches it usually takes the best-scoring candidate, and a
 * near-tie between two real companies is indistinguishable in the output from a confident match.
 * The edge that follows is then attached to the wrong company, is corroborated by a second model
 * that makes the same choice for the same reason, and travels into a multi-hop answer.
 *
 * <p>So ambiguity is a first-class outcome here. Two candidates valid at the same instant produce
 * an abstention carrying both, not a coin flip. The cost is a question that sometimes comes back
 * "which Apple did you mean?"; the alternative cost is a supply-chain claim about a record label.
 */
@Service
class EntityResolver {

    private final EntityRepository entities;

    EntityResolver(EntityRepository entities) {
        this.entities = entities;
    }

    Resolution resolve(String mention, AsOf at) {
        if (mention == null || mention.isBlank()) {
            throw new IllegalArgumentException("mention must not be blank");
        }

        List<CanonicalEntity> candidates = entities.findByName(mention, at);

        if (candidates.isEmpty()) {
            return new Resolution.Abstained(
                    new Abstention(
                            "entity-resolution",
                            AbstentionReason.NO_EVIDENCE_EXISTS,
                            "no entity was known as '%s' at %s".formatted(mention, at),
                            0f),
                    List.of());
        }

        if (candidates.size() > 1) {
            // Deliberately not ranked. Any tie-break available here -- alias count, recency,
            // whichever row the index returned first -- is a property of the corpus rather than
            // of the question, and picking on one produces a confident answer with no basis. The
            // caller has the surrounding sentence and can disambiguate; this layer does not.
            return new Resolution.Abstained(
                    new Abstention(
                            "entity-resolution",
                            AbstentionReason.CONFLICTING_EVIDENCE,
                            "'%s' resolves to %d entities at %s"
                                    .formatted(mention, candidates.size(), at),
                            1f),
                    candidates.stream().map(entity -> entity.id().value()).toList());
        }

        CanonicalEntity only = candidates.getFirst();
        return new Resolution.Resolved(only.id().value(), only.canonicalName(), confidenceOf(only, mention, at));
    }

    /**
     * Higher for a match on the name the entity is filed under than on a historical alias.
     *
     * <p>Not a probability and not presented as one. It separates "this is what the company is
     * called" from "this is what it used to be called", which is the distinction a reader of the
     * answer needs, and stops short of the calibrated score that a single unlabelled corpus cannot
     * support.
     */
    private static double confidenceOf(CanonicalEntity entity, String mention, AsOf at) {
        String probe = Alias.normalise(mention);
        if (Alias.normalise(entity.canonicalName()).equals(probe)) {
            return 1.0d;
        }
        return entity.aliasNamed(mention, at).isPresent() ? 0.8d : 0.5d;
    }
}
