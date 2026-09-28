/**
 * Knowledge: canonical entities, the aliases they were known by, and the bitemporal edges between
 * them.
 *
 * <h2>Why edges are withheld by default</h2>
 *
 * <p>A relationship extracted by a language model is a hypothesis. The industry default is to
 * write it into the graph and hope evaluation catches the bad ones, which fails in a specific way:
 * a wrong edge is indistinguishable from a right one at the point of use, and multi-hop traversal
 * multiplies it — one bad edge at hop two contaminates every path through it.
 *
 * <p>So an edge is born {@code WITHHELD} and there is no method that makes it visible without a
 * {@link com.atlas.knowledge.domain.model.Verification}, which cannot be constructed from two
 * extractors of the same model family. Disagreement is not discarded: it goes to the review queue,
 * where it is also the platform's best per-source quality signal.
 *
 * <h2>Why every traversal is bitemporal</h2>
 *
 * <p>A supplier relationship that ended two years ago must not contaminate this quarter's
 * analysis, and an extraction corrected last week must not silently rewrite the answer a client
 * was given in March. Those are two different clocks and the port demands both.
 *
 * <h2>Relationship to the rest</h2>
 *
 * <p>Knowledge is downstream of {@code catalog}: it subscribes to retraction so that edges derived
 * from a withdrawn document stop being traversable. It never reaches back, and it holds no
 * content — only identity, structure, and the citations that let any edge be walked back to the
 * sentence it came from.
 */
package com.atlas.knowledge;
