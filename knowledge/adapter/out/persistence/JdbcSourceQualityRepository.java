package com.atlas.knowledge.adapter.out.persistence;

import com.atlas.knowledge.api.SourceQuality;
import com.atlas.knowledge.domain.port.SourceQualityRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Counts the quality signal out of the tables that already hold it.
 *
 * <p>SQL rather than loading aggregates. This is a read model over hundreds of thousands of rows
 * answering "how many, grouped by source", which is the one thing a relational database is
 * unambiguously better at than any object graph.
 *
 * <p>Edges are attributed by their citations' {@code source_name}, counted {@code distinct},
 * because an edge supported by three quotes from one filing is one extraction and not three.
 */
@Repository
class JdbcSourceQualityRepository implements SourceQualityRepository {

    private static final String PROPOSED = """
            select count(distinct c.relationship_id)
            from knowledge_edge_citation c
            where c.source_name = ?
            """;

    private static final String DISPUTES = """
            select
              count(*) filter (where true)                        as raised,
              count(*) filter (where outcome = 'REFUTED')         as refuted,
              count(*) filter (where outcome = 'CONFIRMED')       as confirmed,
              count(*) filter (where outcome = 'UNDECIDABLE')     as undecidable
            from knowledge_review_item
            where source_id = ?
            """;

    private static final String SOURCES = """
            select distinct source_name from knowledge_edge_citation order by source_name
            """;

    private final JdbcTemplate jdbc;

    JdbcSourceQualityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SourceQuality forSource(String sourceId) {
        Long proposed = jdbc.queryForObject(PROPOSED, Long.class, sourceId);

        return jdbc.queryForObject(
                DISPUTES,
                (rs, row) -> new SourceQuality(
                        sourceId,
                        proposed == null ? 0L : proposed,
                        rs.getLong("raised"),
                        rs.getLong("refuted"),
                        rs.getLong("confirmed"),
                        rs.getLong("undecidable")),
                sourceId);
    }

    @Override
    public List<SourceQuality> all() {
        return jdbc.queryForList(SOURCES, String.class).stream().map(this::forSource).toList();
    }
}
