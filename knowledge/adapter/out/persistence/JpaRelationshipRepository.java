package com.atlas.knowledge.adapter.out.persistence;

import com.atlas.knowledge.domain.model.EdgeStatus;
import com.atlas.knowledge.domain.model.EntityId;
import com.atlas.knowledge.domain.model.ExtractorId;
import com.atlas.knowledge.domain.model.Relationship;
import com.atlas.knowledge.domain.model.RelationshipId;
import com.atlas.knowledge.domain.model.RelationshipType;
import com.atlas.knowledge.domain.model.Verification;
import com.atlas.knowledge.domain.port.RelationshipRepository;
import com.atlas.shared.provenance.AuthorityTier;
import com.atlas.shared.provenance.BoundingBox;
import com.atlas.shared.provenance.Citation;
import com.atlas.shared.provenance.SpanRef;
import com.atlas.shared.temporal.BitemporalAsOf;
import com.atlas.shared.temporal.Validity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface RelationshipJpaRepository extends JpaRepository<RelationshipRow, String> {

    /**
     * Candidate edges out of an entity in either direction.
     *
     * <p>Narrowed by endpoint and by <em>ever having been verified</em> — the two predicates an
     * index can serve. Deliberately not narrowed by {@code status = 'ACTIVE'}: an edge retracted
     * last week was legitimately believed in March, and a replay of a March answer has to see it.
     * Filtering on the current status here would make every historical traversal silently return
     * today's graph, with no error and no symptom.
     *
     * <p>The four-way instant comparison is then applied in the aggregate rather than in SQL, so
     * the visibility rule exists in one language instead of two — the SQL copy being the one no
     * unit test would cover.
     */
    @Query("""
            select r from RelationshipRow r
            where (r.subjectId = :entityId or r.objectId = :entityId)
              and r.verifiedAt is not null
            """)
    List<RelationshipRow> findCandidateNeighbours(@Param("entityId") String entityId);

    @Query("""
            select r from RelationshipRow r
            where r.relationshipId in (
                select c.relationshipId from EdgeCitationRow c where c.docId = :docId)
            """)
    List<RelationshipRow> findDerivedFromDocument(@Param("docId") String docId);
}

interface EdgeCitationJpaRepository extends JpaRepository<EdgeCitationRow, String> {

    List<EdgeCitationRow> findByRelationshipId(String relationshipId);

    void deleteByRelationshipId(String relationshipId);
}

/** Driven adapter for the graph. */
@Repository
class JpaRelationshipRepository implements RelationshipRepository {

    private final RelationshipJpaRepository rows;
    private final EdgeCitationJpaRepository citations;

    JpaRelationshipRepository(RelationshipJpaRepository rows, EdgeCitationJpaRepository citations) {
        this.rows = rows;
        this.citations = citations;
    }

    @Override
    public Optional<Relationship> findById(RelationshipId id) {
        return rows.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Relationship> neighbours(EntityId from, BitemporalAsOf at) {
        return rows.findCandidateNeighbours(from.value()).stream()
                .map(this::toDomain)
                .filter(edge -> edge.isVisibleAt(at))
                // An asymmetric predicate read backwards inverts its meaning: arriving at a
                // supplier and walking to its subject would report the customer as the supplier.
                .filter(edge -> edge.subject().equals(from) || edge.type().isSymmetric())
                .toList();
    }

    @Override
    public List<Relationship> findDerivedFrom(String documentId) {
        return rows.findDerivedFromDocument(documentId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(Relationship relationship) {
        RelationshipRow row =
                rows.findById(relationship.id().value()).orElseGet(RelationshipRow::new);
        row.relationshipId = relationship.id().value();
        row.subjectId = relationship.subject().value();
        row.relationshipType = relationship.type().name();
        row.objectId = relationship.object().value();
        row.validFrom = relationship.validity().from();
        row.validTo = relationship.validity().to();
        row.recordedAt = relationship.recordedAt();
        row.supersededAt = relationship.supersededAt();
        row.status = relationship.status().name();
        row.statusReason = relationship.statusReason();
        row.extractorModel = relationship.extractedBy().model();
        row.extractorFamily = relationship.extractedBy().family();

        Verification verification = relationship.verification();
        if (verification != null) {
            row.verifiedFirstModel = verification.first().model();
            row.verifiedFirstFamily = verification.first().family();
            row.verifiedSecondModel = verification.second().model();
            row.verifiedSecondFamily = verification.second().family();
            row.verifiedAt = verification.at();
            row.verifiedStatement = verification.agreedStatement();
        }
        rows.save(row);

        citations.deleteByRelationshipId(relationship.id().value());
        int ordinal = 0;
        for (Citation citation : relationship.provenance()) {
            EdgeCitationRow citationRow = new EdgeCitationRow();
            citationRow.citationId = "%s#%d".formatted(relationship.id().value(), ordinal++);
            citationRow.relationshipId = relationship.id().value();
            citationRow.spanId = citation.span().spanId();
            citationRow.docId = citation.span().docId();
            citationRow.page = citation.span().page();
            citationRow.charStart = citation.span().charStart();
            citationRow.charEnd = citation.span().charEnd();
            citationRow.bboxX0 = citation.span().bbox().x0();
            citationRow.bboxY0 = citation.span().bbox().y0();
            citationRow.bboxX1 = citation.span().bbox().x1();
            citationRow.bboxY1 = citation.span().bbox().y1();
            citationRow.quotedText = citation.quotedText();
            citationRow.authority = citation.authority().name();
            citationRow.publishedAt = citation.publishedAt();
            citationRow.sourceName = citation.sourceName();
            citations.save(citationRow);
        }
    }

    private Relationship toDomain(RelationshipRow row) {
        Relationship relationship = new Relationship(
                RelationshipId.of(row.relationshipId),
                EntityId.of(row.subjectId),
                RelationshipType.valueOf(row.relationshipType),
                EntityId.of(row.objectId),
                new Validity(row.validFrom, row.validTo),
                row.recordedAt,
                ExtractorId.model(row.extractorModel, row.extractorFamily),
                citationsOf(row.relationshipId));

        Verification verification = row.verifiedFirstModel == null
                ? null
                : new Verification(
                        ExtractorId.model(row.verifiedFirstModel, row.verifiedFirstFamily),
                        ExtractorId.model(row.verifiedSecondModel, row.verifiedSecondFamily),
                        row.verifiedAt,
                        row.verifiedStatement);

        relationship.rehydrate(
                EdgeStatus.valueOf(row.status), verification, row.supersededAt, row.statusReason);
        return relationship;
    }

    private List<Citation> citationsOf(String relationshipId) {
        return citations.findByRelationshipId(relationshipId).stream()
                .map(row -> new Citation(
                        new SpanRef(
                                row.spanId,
                                row.docId,
                                row.page,
                                new BoundingBox(row.bboxX0, row.bboxY0, row.bboxX1, row.bboxY1),
                                row.charStart,
                                row.charEnd),
                        row.quotedText,
                        AuthorityTier.valueOf(row.authority),
                        row.publishedAt,
                        row.sourceName))
                .toList();
    }
}
