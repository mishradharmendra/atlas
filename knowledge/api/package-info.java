/**
 * The knowledge context's published language.
 *
 * <p>Everything outside this package is internal. Callers get entity resolution, traversal and the
 * review queue; they do not get the aggregates, because an aggregate handed across a module
 * boundary is an aggregate whose invariants are now everyone's problem.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.knowledge.api;
