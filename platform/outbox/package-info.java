/**
 * Supervision of the Modulith event outbox.
 *
 * <p>The outbox itself is Spring Modulith's. What is here is the operational half nobody supplies
 * by default: resubmitting what stalled, reporting what resubmission cannot fix, and keeping the
 * table from growing without bound.
 */
package com.atlas.platform.outbox;
