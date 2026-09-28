package com.atlas.skill.application;

import com.atlas.skill.api.PublishSkillCommand;
import com.atlas.skill.api.SkillApi;
import com.atlas.skill.api.SkillView;
import com.atlas.skill.domain.model.Condition;
import com.atlas.skill.domain.model.FeedbackKind;
import com.atlas.skill.domain.model.FeedbackSpec;
import com.atlas.skill.domain.model.Guardrail;
import com.atlas.skill.domain.model.OnFail;
import com.atlas.skill.domain.model.Skill;
import com.atlas.skill.domain.model.SkillId;
import com.atlas.skill.domain.model.StepSpec;
import com.atlas.skill.domain.port.SkillRepository;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the skill catalogue. */
@Service
class SkillService implements SkillApi {

    private final SkillRepository skills;
    private final Clock clock;

    SkillService(SkillRepository skills, Clock clock) {
        this.skills = skills;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String publish(PublishSkillCommand command) {
        SkillId id = SkillId.of(command.skillId());
        List<StepSpec> steps = command.steps().stream().map(SkillService::step).toList();

        Optional<Skill> existing = skills.find(id, command.version());
        if (existing.isPresent()) {
            Skill published = existing.get();
            if (!sameSteps(published.steps(), steps)) {
                // Republishing altered steps at the same version would make every run already
                // recorded against it describe a procedure that no longer exists.
                throw new IllegalStateException(
                        ("skill %s is already published with different steps. Publish a new "
                                        + "version instead; reusing this one would make runs "
                                        + "already recorded against it unattributable")
                                .formatted(published.versionedId()));
            }
            return published.versionedId();
        }

        Skill skill = new Skill(id, command.version(), command.name(), command.intent(), steps);
        skill.freeze(clock.instant());
        skills.save(skill);
        return skill.versionedId();
    }

    @Override
    public SkillView find(String skillId, int version) {
        Skill skill = skills.find(SkillId.of(skillId), version)
                .orElseThrow(() -> new NoSuchElementException(
                        "no skill %s version %d".formatted(skillId, version)));
        return view(skill);
    }

    private static boolean sameSteps(List<StepSpec> published, List<StepSpec> candidate) {
        return published.equals(candidate);
    }

    private static StepSpec step(PublishSkillCommand.StepDefinition definition) {
        return new StepSpec(
                definition.stepId(),
                definition.description(),
                definition.taskType(),
                definition.mandatory(),
                definition.producesProse(),
                enums(definition.preconditions(), Condition::valueOf),
                enums(definition.postconditions(), Condition::valueOf),
                enums(definition.guardrails(), Guardrail::valueOf),
                OnFail.valueOf(definition.onFail()),
                definition.maxAttempts(),
                new FeedbackSpec(
                        definition.rubricId(),
                        FeedbackKind.valueOf(definition.feedbackKind()),
                        definition.blocking()));
    }

    private static <T> Set<T> enums(Set<String> names, java.util.function.Function<String, T> parse) {
        return names.stream().map(parse).collect(Collectors.toUnmodifiableSet());
    }

    static SkillView view(Skill skill) {
        return new SkillView(
                skill.id().value(),
                skill.version(),
                skill.versionedId(),
                skill.name(),
                skill.intent(),
                skill.isFrozen(),
                skill.stepIds(),
                skill.mandatorySteps().stream().map(StepSpec::stepId).toList(),
                // The union across steps. A guardrail declared on any step constrains the whole
                // run: "this procedure must not commingle MNPI" is not satisfied by keeping it
                // out of one step and letting it into the next.
                skill.steps().stream()
                        .flatMap(step -> step.guardrails().stream())
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
