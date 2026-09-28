/**
 * Research runs: the execution of a skill, and everything needed to explain or reproduce it.
 *
 * <h2>Why a run is a saga and not a transaction</h2>
 *
 * <p>A run calls models, retrieves from indexes, writes to memory and spends money, over minutes.
 * None of that can be rolled back: the tokens are spent whatever happens next. So the aggregate
 * is built around forward recovery — every step records what it did, and a failure produces a
 * resumable state rather than an attempted undo.
 *
 * <h2>Why suspension, not truncation</h2>
 *
 * <p>The tempting behaviour on a budget breach is to stop and return what you have. It is also
 * the worst one available: a truncated answer looks exactly like a complete answer, and the
 * analyst who acts on it has no way to know that the run stopped three sources short. So a breach
 * suspends. A suspended run is visible, resumable with a raised ceiling, and cannot be mistaken
 * for a finished one.
 *
 * <h2>Why the trace is part of the domain</h2>
 *
 * <p>An agent that cannot be explained cannot be sold into this market and cannot be debugged.
 * The trace is therefore not logging — it is the record from which a run is replayed, the input
 * to trajectory evaluation, and the answer to "why did it say that?". Logging is what you add
 * afterwards and discover is missing the one field you need.
 */
package com.atlas.research;
