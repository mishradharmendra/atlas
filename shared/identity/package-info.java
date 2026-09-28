/**
 * Identity types, exposed as a named interface of the shared kernel.
 *
 * <p>Sub-packages of a Spring Modulith module are internal by default, which is the correct
 * default: {@code public} in Java cannot express "public to my module only", so without this
 * annotation every internal helper becomes part of the contract by accident.
 *
 * <p>Declaring the exposure explicitly makes the shared kernel's surface a deliberate, reviewable
 * decision rather than a consequence of where a file happened to be put.
 */
@org.springframework.modulith.NamedInterface("identity")
package com.atlas.shared.identity;
