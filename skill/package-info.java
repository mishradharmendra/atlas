/**
 * Skills: the declarative procedures an agent may run.
 *
 * <h2>Why a skill is data rather than code</h2>
 *
 * <p>The tempting shape is a Java class per skill with the steps as method calls. It is shorter
 * to write and it makes three things impossible: pinning the exact procedure a past run executed,
 * comparing two versions of a procedure on the same questions, and letting anyone who is not a
 * Java developer change one.
 *
 * <p>The middle of those is the one that matters. Improving an agent means changing a plan and
 * measuring whether the change helped — and if the plan lives in a deployed jar, "did this help?"
 * requires a release per experiment, which is slow enough that in practice nobody measures.
 *
 * <h2>Why skills freeze</h2>
 *
 * <p>Same reason rubrics do. A skill that can be edited after a run has been graded makes the
 * grade unfalsifiable: the procedure the number describes no longer exists. Revision produces a
 * new version, and a run records the version it executed.
 *
 * <h2>Why every step declares how it is judged</h2>
 *
 * <p>A {@link com.atlas.skill.domain.model.FeedbackSpec} is mandatory on every step. A step with
 * no declared feedback cannot be improved: there is no signal to optimise and no way to tell a
 * regression from a change. Requiring it at authoring time is what stops a skill accreting
 * unmeasured steps that nobody dares delete.
 */
package com.atlas.skill;
