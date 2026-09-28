package com.atlas.skill.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A versioned, frozen procedure an agent may execute.
 *
 * <h2>Invariants</h2>
 *
 * <ol>
 *   <li><b>Frozen after publication.</b> Same reason a rubric freezes: a skill edited after a run
 *       was graded makes the grade unfalsifiable, because the procedure the number describes no
 *       longer exists. Revision produces a new version.
 *   <li><b>Step ids are unique and ordered.</b> The trace is keyed by step id; duplicates make a
 *       replay ambiguous about which occurrence it is reproducing.
 *   <li><b>At least one mandatory step.</b> A skill where every step may be skipped can emit an
 *       artefact having done nothing, and it will look identical to one that worked.
 *   <li><b>The last step may not be {@link OnFail#CONTINUE_DEGRADED}.</b> Nothing after it can
 *       notice the degradation, so the mark would never be acted on.
 * </ol>
 */
public class Skill {

    private final SkillId id;
    private final int version;
    private final String name;
    private final String intent;
    private final List<StepSpec> steps;

    private boolean frozen;
    private Instant frozenAt;

    public Skill(SkillId id, int version, String name, String intent, List<StepSpec> steps) {
        this.id = Objects.requireNonNull(id, "skill id");
        if (version < 1) {
            throw new IllegalArgumentException("skill version starts at 1, was " + version);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name must not be blank");
        }
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException(
                    "a skill must state what question it answers; without it nobody can tell "
                            + "whether a new skill duplicates an existing one");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("a skill needs at least one step");
        }

        Set<String> ids = new LinkedHashSet<>();
        for (StepSpec step : steps) {
            if (!ids.add(step.stepId())) {
                throw new IllegalArgumentException(
                        ("skill %s repeats step id '%s'. The trace is keyed by step id, so a "
                                        + "replay could not tell which occurrence it is reproducing")
                                .formatted(id, step.stepId()));
            }
        }
        if (steps.stream().noneMatch(StepSpec::mandatory)) {
            throw new IllegalArgumentException(
                    ("skill %s has no mandatory step. Every step could be skipped and the run "
                                    + "would still emit an artefact, indistinguishable from one "
                                    + "that did the work")
                            .formatted(id));
        }
        if (steps.getLast().onFail() == OnFail.CONTINUE_DEGRADED) {
            throw new IllegalArgumentException(
                    ("the last step of %s is CONTINUE_DEGRADED; nothing runs after it to notice "
                                    + "the degradation, so the mark would never be acted on")
                            .formatted(id));
        }

        this.version = version;
        this.name = name;
        this.intent = intent;
        this.steps = List.copyOf(steps);
    }

    /** Closes the skill to further change. Idempotent. */
    public void freeze(Instant at) {
        Objects.requireNonNull(at, "freeze instant");
        if (frozen) {
            return;
        }
        this.frozen = true;
        this.frozenAt = at;
    }

    /**
     * Produces the next version rather than editing this one.
     *
     * <p>Refuses on an unfrozen skill: revising a draft in place is ordinary editing, and calling
     * it a revision would put a version number on something that never existed as a distinct
     * procedure.
     */
    public Skill revise(List<StepSpec> revisedSteps) {
        if (!frozen) {
            throw new IllegalStateException(
                    "skill %s is not frozen; edit it in place rather than versioning a draft"
                            .formatted(id));
        }
        return new Skill(id, version + 1, name, intent, revisedSteps);
    }

    /** Restores persisted state without replaying the freeze. */
    public void rehydrate(boolean frozen, Instant frozenAt) {
        this.frozen = frozen;
        this.frozenAt = frozenAt;
    }

    public List<StepSpec> mandatorySteps() {
        return steps.stream().filter(StepSpec::mandatory).toList();
    }

    /** The identity a run records so that "which procedure produced this?" stays answerable. */
    public String versionedId() {
        return "%s@v%d".formatted(id.value(), version);
    }

    public SkillId id() {
        return id;
    }

    public int version() {
        return version;
    }

    public String name() {
        return name;
    }

    public String intent() {
        return intent;
    }

    public List<StepSpec> steps() {
        return steps;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    public List<String> stepIds() {
        return new ArrayList<>(steps.stream().map(StepSpec::stepId).toList());
    }
}
