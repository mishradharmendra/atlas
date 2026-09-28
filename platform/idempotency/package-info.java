/**
 * Idempotency keys: making a retried write safe to send twice.
 *
 * <p>Internal to the platform. Nothing in the domain knows this exists, which is the point — an
 * endpoint should not have to opt in to being safe to retry, and a service that had to think
 * about it would think about it inconsistently.
 */
package com.atlas.platform.idempotency;
