/**
 * The public interface of the evaluation context.
 *
 * <p>Note what is absent: nothing here accepts a system output, a run identifier, or anything that
 * could be used to fetch one. Evaluation grades against a standard written from the question, and
 * a standard that can see the answer is a standard derived from it.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.evaluation.api;
