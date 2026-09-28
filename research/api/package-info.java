/**
 * The research context's published language.
 *
 * <p>Callers start runs, record what a step did, and ask for the artefact. They do not get the
 * {@code Run} aggregate: an aggregate handed across a module boundary is an aggregate whose
 * invariants become everyone's problem, and the invariants here are the product.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.research.api;
