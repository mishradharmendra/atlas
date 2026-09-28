/**
 * Entitlement: who may see what, as of when.
 *
 * <h2>Why this is the most load-bearing context in the platform</h2>
 *
 * <p>Licensed research is visible only to firms holding the contract. Some content is embargoed
 * for a period before it widens. Customer-uploaded documents inherit the ACLs of the system they
 * came from. All three must be enforced <em>inside</em> the retrieval operation, as a pre-filter
 * on the index traversal — never as a filter applied to results afterwards.
 *
 * <p>The reason is not tidiness. Post-filtering silently destroys recall: retrieve the top 100 by
 * similarity, drop the 60 this principal may not see, return 40, and believe you performed a
 * top-100 search. In a conversational product that is a poor result. In an agentic product it is
 * considerably worse — the agent concludes the evidence does not exist, states that as fact, and
 * the user has no way to tell the difference between "nothing was written about this" and "you
 * were not allowed to see what was written about this".
 *
 * <p>Hence {@code Existence.NONE_PERMITTED} in the retrieval contract, and hence this context
 * publishes an immutable, signed, expiring snapshot rather than answering per-document questions
 * at query time: a per-document ACL check does not survive contact with an approximate
 * nearest-neighbour traversal over billions of vectors.
 *
 * <h2>Design stance</h2>
 *
 * <p>This context is an Open Host Service. {@code EntitlementSnapshot} is its published payload,
 * consumed by the Python retrieval plane, the agent's context assembler, and memory retrieval
 * alike. It fails closed: absent, expired or unverifiable means no results, never unfiltered ones.
 */
package com.atlas.entitlement;
