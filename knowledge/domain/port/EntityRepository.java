package com.atlas.knowledge.domain.port;

import com.atlas.knowledge.domain.model.CanonicalEntity;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.shared.temporal.AsOf;
import java.util.List;
import java.util.Optional;

/** Durable store for canonical entities and their aliases. */
public interface EntityRepository {

    Optional<CanonicalEntity> findById(EntityId id);

    /**
     * Every entity that answered to this name at that instant.
     *
     * <p>Returns a list, not an {@code Optional}. "Apple" in 1985 is two companies and the store
     * has no basis for choosing between them; a signature that could only return one would force
     * this method to pick arbitrarily and would hide the ambiguity from the only component
     * equipped to act on it. Deciding — or abstaining — is the resolver's job.
     */
    List<CanonicalEntity> findByName(String name, AsOf at);

    /**
     * Every entity the graph holds, by canonical name.
     *
     * <p>So a caller can say what it covers. A question box with no statement of coverage puts
     * the burden on the asker to discover by trial that an issuer is absent, and an empty
     * result is indistinguishable from a broken system.
     */
    List<CanonicalEntity> all();

    void save(CanonicalEntity entity);
}
