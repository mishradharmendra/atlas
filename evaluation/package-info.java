/**
 * Evaluation: is it getting better, and can you tell why.
 *
 * <h2>Why this context exists before the agent does</h2>
 *
 * <p>A system that generates answers can always be made to look better. Without a measurement
 * built first, the measurement gets built later — by which point it is built by people who know
 * what the system currently does, and it encodes that. Every subsequent comparison is then against
 * a yardstick shaped by the thing it is measuring.
 *
 * <h2>Separate Ways from research</h2>
 *
 * <p>This context shares only the {@code shared} kernel. It does not depend on {@code research},
 * and that is a deliberate refusal rather than an oversight: an evaluation that reads a run's
 * internals can only grade systems shaped like the one it was written against, and swapping the
 * agent then means rewriting the benchmark that was supposed to judge the swap.
 *
 * <h2>The two things that are enforced rather than encouraged</h2>
 *
 * <p>A {@code Rubric} is immutable once frozen. Not by policy — by the aggregate refusing. A
 * criterion edited after results exist silently redefines every number previously reported under
 * that rubric's name, and nothing about the reports will look wrong.
 *
 * <p>The rubric generator's input cannot contain a system output. If a rubric can see the answer,
 * it grades the answer's shape rather than the question's requirements, and the benchmark drifts
 * toward whatever the system already produces. Making that unrepresentable in the type is the
 * only version of this rule that survives a deadline.
 */
package com.atlas.evaluation;
