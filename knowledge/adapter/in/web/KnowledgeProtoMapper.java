package com.atlas.knowledge.adapter.in.web;

import com.atlas.contracts.v1.EdgeCitation;
import com.atlas.contracts.v1.Hop;
import com.atlas.contracts.v1.Path;
import com.atlas.knowledge.api.HopView;
import com.atlas.knowledge.api.PathView;
import com.atlas.shared.provenance.AuthorityTier;
import com.atlas.shared.provenance.BoundingBox;
import com.atlas.shared.provenance.Citation;
import com.atlas.shared.provenance.SpanRef;
import java.time.Instant;
import java.util.List;

/**
 * Anti-corruption layer between the published language and the domain.
 *
 * <p>Lives here rather than in the domain because a protobuf builder cannot express "this cannot
 * be constructed in an invalid state" — every field is optional and every message has a no-arg
 * default. Translating at the edge is what lets {@code Citation} keep refusing a missing quote
 * while the wire format keeps its proto3 semantics.
 *
 * <p>The bounding box does not cross the wire. It is viewer geometry, the pipeline has no use for
 * it, and carrying it would put a four-float shape in the contract that could only ever be
 * defaulted on the way back in.
 */
final class KnowledgeProtoMapper {

    private KnowledgeProtoMapper() {}

    static List<Citation> citations(List<EdgeCitation> wire) {
        return wire.stream().map(KnowledgeProtoMapper::citation).toList();
    }

    private static Citation citation(EdgeCitation wire) {
        return new Citation(
                new SpanRef(
                        wire.getSpanId(),
                        wire.getDocId(),
                        wire.getPage(),
                        BoundingBox.none(),
                        wire.getCharStart(),
                        wire.getCharEnd()),
                wire.getQuotedText(),
                AuthorityTier.valueOf(wire.getAuthority()),
                Instant.parse(wire.getPublishedAt()),
                wire.getSourceName());
    }

    static Path path(PathView view) {
        return Path.newBuilder()
                .setFromEntityId(view.fromEntityId())
                .setToEntityId(view.toEntityId())
                .addAllHops(view.hops().stream().map(KnowledgeProtoMapper::hop).toList())
                .build();
    }

    private static Hop hop(HopView view) {
        return Hop.newBuilder()
                .setSubjectId(view.subjectId())
                .setSubjectName(view.subjectName())
                .setRelationshipType(view.relationshipType())
                .setObjectId(view.objectId())
                .setObjectName(view.objectName())
                .addAllEvidence(view.evidence().stream().map(KnowledgeProtoMapper::wire).toList())
                .build();
    }

    private static EdgeCitation wire(Citation citation) {
        return EdgeCitation.newBuilder()
                .setSpanId(citation.span().spanId())
                .setDocId(citation.span().docId())
                .setPage(citation.span().page())
                .setCharStart(citation.span().charStart())
                .setCharEnd(citation.span().charEnd())
                .setQuotedText(citation.quotedText())
                .setAuthority(citation.authority().name())
                .setPublishedAt(citation.publishedAt().toString())
                .setSourceName(citation.sourceName())
                .build();
    }
}
