package com.atlas.ingestion.domain.port;

import com.atlas.ingestion.domain.model.RightsMetadata;
import java.util.Optional;

/** Durable store for what each source's contract permits. */
public interface RightsRepository {

    /**
     * The rights recorded for a source, or empty if no contract has been registered.
     *
     * <p>Empty is not "permitted by default". Callers must treat an absent contract as granting
     * nothing — see {@link RightsMetadata#forUnlicensedSource(String)} — because the alternative
     * is a pipeline that processes whatever it is handed and creates the exposure in bulk, at
     * machine speed, months before anyone looks.
     */
    Optional<RightsMetadata> findBySourceId(String sourceId);

    void save(RightsMetadata rights);
}
