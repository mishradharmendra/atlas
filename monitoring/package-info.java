/**
 * Monitoring: telling someone that something they care about changed, and not telling them
 * anything else.
 *
 * <h2>The failure mode this module is designed around</h2>
 *
 * <p>Monitoring does not fail by missing things. It fails by sending too much, at which point the
 * user mutes it, and from then on it misses everything. Every rule here is about the second
 * failure, because the first is the one people build for and the second is the one that happens.
 *
 * <h2>Three separate mechanisms, deliberately not merged</h2>
 *
 * <ul>
 *   <li><b>Deduplication</b> is exact and permanent: one real-world fact produces one trigger,
 *       ever. Five outlets reporting one acquisition is one alert.
 *   <li><b>The quiet period</b> is about repetition: the same subject and the same kind of change
 *       within a short window is one story, even when the facts genuinely differ.
 *   <li><b>The fan-out cap</b> is about volume: beyond a rate, alerts collapse into a digest.
 * </ul>
 *
 * <p>They look similar enough to merge and must not be. Collapsing dedup into the quiet period
 * would let a duplicate through once the window lapsed; collapsing the quiet period into the
 * fan-out cap would count a repeated story against the budget that exists to catch a flood.
 *
 * <h2>Nothing is ever dropped silently</h2>
 *
 * <p>Suppressed triggers are stored with the reason they were suppressed. The only question ever
 * asked of a monitoring system is "why was I not told about this", and a system that discards
 * what it chose not to send cannot answer it. It is also the sole evidence that the thresholds
 * are set wrong, which is otherwise invisible: over-suppression looks exactly like a quiet week.
 */
package com.atlas.monitoring;
