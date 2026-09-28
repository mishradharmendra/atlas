/**
 * Cross-cutting infrastructure shared by every bounded context.
 *
 * <h2>What belongs here</h2>
 *
 * <p>Time, request-scoped context, idempotency, budget enforcement, event publication plumbing.
 * The test for membership is whether a type is <em>infrastructure</em> rather than <em>domain</em>:
 * a deadline, a correlation id or a thread pool is infrastructure; a fiscal period is not.
 *
 * <p>Domain packages are forbidden from depending on this module — enforced by
 * {@code ArchitectureRulesTest#domainDoesNotSeePlatform}. A domain object that needs the time
 * receives an {@code Instant} as a parameter; it never reaches for a clock. That rule is what
 * makes "does this snapshot expire correctly after 15 minutes?" a three-line test rather than a
 * test that sleeps.
 */
package com.atlas.platform;
