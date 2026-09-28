package com.atlas.entitlement.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Turns a presented snapshot into a verified identity, or refuses.
 *
 * <p>A snapshot is already a bearer credential: signed by the platform, naming a tenant and a
 * principal, and carrying an expiry. It was being used only to filter retrieval; using it to
 * establish who the caller is closes the gap where callers named their own tenant.
 *
 * <p>Returns {@link Optional} rather than throwing, because "no credential" and "a bad
 * credential" are both ordinary outcomes at an authentication boundary and neither is
 * exceptional. What must never happen is a third outcome where an unverifiable credential is
 * treated as absent and the request proceeds unauthenticated.
 */
public interface SnapshotAuthenticator {

    Optional<VerifiedIdentity> authenticate(String presentedSnapshot, Instant now);
}
