/**
 * The public interface of the entitlement context.
 *
 * <p>Other modules and the Python retrieval plane depend on this package and nothing deeper.
 * Everything else in {@code com.atlas.entitlement} is internal and enforced as such by Spring
 * Modulith — reaching into {@code domain.model} from another module compiles fine in Java and
 * fails the build here, which is the point: {@code public} cannot express "public to my module".
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.entitlement.api;
