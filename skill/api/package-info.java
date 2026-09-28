/**
 * The skill catalogue's published language.
 *
 * <p>Everything outside this package is internal. Callers publish and read skills; they do not get
 * the aggregate, because an aggregate handed across a module boundary is one whose invariants are
 * now everyone's problem.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.skill.api;
