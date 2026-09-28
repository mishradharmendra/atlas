/**
 * Spend ceilings for a single run.
 *
 * <p>In the shared kernel rather than in {@code platform} because a budget is a <em>domain
 * policy</em>, not infrastructure. It encodes a commercial rule — seat-based pricing with
 * always-on agents means one unbounded run can consume a month of a seat's margin — and the
 * aggregate that enforces it is {@code Run}.
 *
 * <p>It lived in {@code platform} until {@code ArchitectureRulesTest#domainDoesNotSeePlatform}
 * failed on it. The rule was right and the package was wrong: a thread pool is infrastructure, a
 * spend ceiling that suspends a run is a business decision with a number attached.
 */
@org.springframework.modulith.NamedInterface("budget")
package com.atlas.shared.budget;
