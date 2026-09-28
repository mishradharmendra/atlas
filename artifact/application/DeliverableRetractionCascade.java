package com.atlas.artifact.application;

import com.atlas.artifact.domain.model.Artifact;
import com.atlas.artifact.domain.port.ArtifactRepository;
import com.atlas.catalog.events.DocumentRetracted;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The last hop of the takedown cascade, and the one {@link DocumentRetracted} itself predicted
 * would be forgotten.
 *
 * <p>Its javadoc reads: <em>"flag deliverables that cited it. The last of those is the one teams
 * forget."</em> This codebase forgot it for four gates — {@code indexing} and {@code knowledge}
 * both subscribed, deliverables did not — which is worth recording because the comment naming the
 * risk was written by the same hands that then ran the risk.
 *
 * <p>The failure it prevents is the one with a client in it. Purging a withdrawn note from the
 * search index is invisible housekeeping; the deck that quoted the note last Tuesday is sitting
 * in somebody's inbox, and a publisher withdrawing a document does not un-send it. The only
 * honest response is to mark the deliverable and tell whoever sent it.
 */
@Component
class DeliverableRetractionCascade {

    private static final Logger log = LoggerFactory.getLogger(DeliverableRetractionCascade.class);

    private final ArtifactRepository artifacts;
    private final Clock clock;

    DeliverableRetractionCascade(ArtifactRepository artifacts, Clock clock) {
        this.artifacts = artifacts;
        this.clock = clock;
    }

    /** No {@code @Transactional}: {@code @ApplicationModuleListener} already implies REQUIRES_NEW. */
    @ApplicationModuleListener
    void on(DocumentRetracted event) {
        List<Artifact> affected = artifacts.citingDocument(event.documentId());
        String reason = "source document %s was withdrawn: %s"
                .formatted(event.documentId(), event.reason());

        for (Artifact artifact : affected) {
            artifact.flagRetractedSource(event.documentId(), reason, clock.instant());
            artifacts.save(artifact);
        }

        long issued = affected.stream().filter(a -> a.status().isSent()).count();
        if (issued > 0) {
            // Warn rather than info: these are documents already in front of clients, and the
            // count is what somebody has to act on today.
            log.warn(
                    "retraction of {} flagged {} already-sent deliverable(s) of {} total",
                    event.documentId(),
                    issued,
                    affected.size());
        } else {
            log.info(
                    "retraction of {} flagged {} draft deliverable(s)",
                    event.documentId(),
                    affected.size());
        }
    }
}
