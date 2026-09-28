package com.atlas.artifact.domain.port;

import com.atlas.artifact.domain.model.Artifact;
import com.atlas.artifact.domain.model.ArtifactId;
import java.util.List;
import java.util.Optional;

/** Durable store for deliverables. */
public interface ArtifactRepository {

    Optional<Artifact> findById(ArtifactId id);

    /**
     * Every artifact citing a document, whatever its status.
     *
     * <p>Status-blind on purpose, and issued artifacts are the ones that matter most. A draft
     * citing a withdrawn source can be fixed before anyone sees it; an issued one is already in
     * a client's inbox, and the only available response is to say so.
     */
    List<Artifact> citingDocument(String docId);

    /** Deliverables produced by a run. */
    List<Artifact> byRun(String runId);

    /**
     * A tenant's most recent deliverables, newest first.
     *
     * <p>Bounded at the repository rather than trimmed afterwards: a tenant with a year of runs
     * behind it would otherwise load every one of them to display ten.
     */
    List<Artifact> recentFor(String tenantId, int limit);

    void save(Artifact artifact);
}
