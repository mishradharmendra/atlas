/**
 * Artifacts: the deliverables an analyst actually sends — memos, models, comparison tables.
 *
 * <h2>Why a cell is an aggregate concern and not a spreadsheet value</h2>
 *
 * <p>The output of this platform is not an answer, it is a document someone puts their name on
 * and sends to a client. That changes what the data structure has to do. A number in a cell must
 * carry the span it came from, so a reader can click it; it must survive a refresh that
 * recomputes everything around it; and when the source behind it is withdrawn, the document that
 * was already sent has to be findable.
 *
 * <h2>The invariant that matters most</h2>
 *
 * <p><b>An analyst's override is never overwritten.</b> The sequence is always the same: the
 * system extracts a figure, the analyst knows it is wrong and corrects it, the model refreshes
 * overnight, and the correction is gone. It happens once and the analyst stops trusting the tool;
 * there is no second chance with a user who has been burned this way.
 *
 * <p>So a refresh recomputes the machine value and leaves the override standing beside it,
 * flagged as diverging. Both values are kept, because "the source now says something different
 * from what you told me" is exactly the thing worth surfacing.
 *
 * <h2>Why history is append-only</h2>
 *
 * <p>A deliverable that can be edited in place cannot answer "what did we send them in March?",
 * which is the first question asked when a client disputes a number.
 */
package com.atlas.artifact;
