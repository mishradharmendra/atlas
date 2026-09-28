/**
 * The shared kernel: the ubiquitous language of the platform.
 *
 * <h2>Rules for this package</h2>
 *
 * <p>Everything here is an immutable, self-validating value object with no dependency on Spring,
 * JPA, or generated contract classes. These types are used by every bounded context, which makes
 * them powerful and dangerous in equal measure: a change here is a change everywhere, so it
 * requires cross-module review rather than a single approving reviewer.
 *
 * <p>Membership is deliberately narrow. A type belongs in the shared kernel only if <em>every</em>
 * context means the same thing by it. If two contexts would want different invariants on the same
 * noun, that is a signal the noun means two different things and each context should model its
 * own — the alternative is a lowest-common-denominator type that protects nothing.
 */
package com.atlas.shared;
