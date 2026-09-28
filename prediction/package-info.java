/**
 * Predictions and what actually happened: the loop that makes sources earn their keep.
 *
 * <h2>Why the platform records predictions at all</h2>
 *
 * <p>Every other quality signal here is about process — did two extractors agree, did a judge
 * score the rubric, was the citation walkable. Those measure whether the machinery ran properly,
 * and a system can pass all of them while being confidently wrong about the world. A prediction
 * with a resolution date is the only artefact in the platform that the world itself grades.
 *
 * <h2>Falsifiable before it is recorded, not after</h2>
 *
 * <p>The checks live in the constructor because an unfalsifiable prediction is not a prediction
 * that scores badly, it is one that never scores at all — and it improves the average by being
 * quietly dropped from the denominator. "Margins will come under pressure" resolves however the
 * reader wants; "gross margin below 38% in the Q3 filing" does not. Demanding the criterion at
 * the moment of writing costs an argument with the author; demanding it at resolution time means
 * the argument is had by whoever is grading, with an interest in the answer.
 *
 * <h2>Why weights are derived and never assigned</h2>
 *
 * <p>{@link com.atlas.prediction.domain.model.SourceWeight} has no public constructor and no
 * setter, and a test fails the build if one is added. A settable weight is the failure mode this
 * whole module exists to prevent: the first time an important vendor scores badly, someone will
 * want to correct the number rather than the conclusion, and a weight that can be written is one
 * that will be. Deriving it means the only way to change a source's standing is for its claims to
 * come true.
 */
package com.atlas.prediction;
