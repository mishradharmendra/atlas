/**
 * Request identity: who the caller is, established by verification rather than assertion.
 *
 * <p>Exposed as a named interface because it is the one part of {@code platform} that driving
 * adapters in every other module must use. Keeping it internal would leave each controller to
 * invent its own answer to "whose data is this?", and the failure mode of getting that wrong is
 * serving one customer's research to another.
 */
@org.springframework.modulith.NamedInterface("tenant")
package com.atlas.platform.tenant;
