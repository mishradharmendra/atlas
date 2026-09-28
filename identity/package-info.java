/**
 * Who the caller actually is, established from an OIDC identity provider.
 *
 * <h2>The hole this closes</h2>
 *
 * <p>{@code POST /api/v1/entitlement/snapshots} took the tenant and the principal from the
 * request body and signed a credential asserting them. The subject asserted its own identity: a
 * caller could name any tenant and receive a valid credential for it. Everything downstream —
 * the default-deny filter, the per-request tenant, the artifact checks — was correctly enforcing
 * a claim that anybody could make.
 *
 * <h2>Provider-agnostic on purpose</h2>
 *
 * <p>Verification is against an allow-list of issuers, each with its own JWKS endpoint, audience
 * and claim mapping. A local Keycloak is one entry and Okta is another; moving from one to the
 * other is configuration, and both can be live at once during a migration. Nothing in the domain
 * knows which provider signed anything.
 *
 * <p>The allow-list is the security boundary. A verifier that accepts any well-formed token from
 * any issuer is a verifier that accepts tokens minted by an identity provider the attacker
 * controls, and the signature check passes brilliantly.
 *
 * <h2>Why the internal snapshot survives</h2>
 *
 * <p>OIDC establishes who the human is, once, at the edge. The entitlement snapshot carries what
 * they may see, inward, on every call. They answer different questions and have different
 * lifetimes — an ID token is minted by a system that knows nothing about document licences, and
 * re-deriving entitlement on every hop would put the retrieval path behind the identity
 * provider's availability.
 *
 * <h2>Why this is its own module rather than part of platform</h2>
 *
 * <p>It was under {@code platform} first, and Modulith refused the build: {@code platform.tenant}
 * verifies entitlement snapshots, so platform already depends on entitlement, and putting the
 * token verifier there made entitlement depend back on platform. The cycle was real rather than
 * a technicality — "who is this person" and "what may they see" are different questions, and the
 * second is allowed to need the first but not the other way round.
 */
package com.atlas.identity;
