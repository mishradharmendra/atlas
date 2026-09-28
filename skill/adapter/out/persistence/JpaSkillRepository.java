package com.atlas.skill.adapter.out.persistence;

import com.atlas.skill.domain.model.Condition;
import com.atlas.skill.domain.model.FeedbackKind;
import com.atlas.skill.domain.model.FeedbackSpec;
import com.atlas.skill.domain.model.Guardrail;
import com.atlas.skill.domain.model.OnFail;
import com.atlas.skill.domain.model.Skill;
import com.atlas.skill.domain.model.SkillId;
import com.atlas.skill.domain.model.StepSpec;
import com.atlas.skill.domain.port.SkillRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface SkillJpaRepository extends JpaRepository<SkillRow, SkillRow.Key> {
    List<SkillRow> findBySkillIdOrderByVersionAsc(String skillId);
}

/**
 * Driven adapter for skills.
 *
 * <p>Steps are serialised through a mapper owned here rather than through the application's shared
 * one. A global {@code ObjectMapper} is configured for HTTP, and someone tuning it for an API
 * concern — a naming strategy, a null policy — would silently change the on-disk form of every
 * skill written afterwards, leaving the older rows unreadable by the newer code.
 *
 * <p>Enum names are written verbatim, which is the point of a closed set: a step whose
 * {@code on_fail} the running code does not recognise fails loudly at load rather than defaulting
 * to something reasonable.
 */
@Repository
class JpaSkillRepository implements SkillRepository {

    private static final ObjectMapper JSON =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final TypeReference<List<Map<String, Object>>> STEP_LIST = new TypeReference<>() {};

    private final SkillJpaRepository rows;

    JpaSkillRepository(SkillJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Skill> find(SkillId id, int version) {
        SkillRow.Key key = new SkillRow.Key();
        key.skillId = id.value();
        key.version = version;
        return rows.findById(key).map(JpaSkillRepository::toDomain);
    }

    @Override
    public List<Skill> versionsOf(SkillId id) {
        return rows.findBySkillIdOrderByVersionAsc(id.value()).stream()
                .map(JpaSkillRepository::toDomain)
                .toList();
    }

    @Override
    public void save(Skill skill) {
        SkillRow.Key key = new SkillRow.Key();
        key.skillId = skill.id().value();
        key.version = skill.version();

        SkillRow existing = rows.findById(key).orElse(null);
        if (existing != null && existing.frozen) {
            throw new IllegalStateException(
                    ("skill %s@v%d is frozen and cannot be overwritten. A frozen skill edited in "
                                    + "place would invalidate every run already graded against it, "
                                    + "silently: the procedure the grade describes would no longer "
                                    + "exist. Publish a new version.")
                            .formatted(skill.id(), skill.version()));
        }

        SkillRow row = existing == null ? new SkillRow() : existing;
        row.skillId = skill.id().value();
        row.version = skill.version();
        row.name = skill.name();
        row.intent = skill.intent();
        row.steps = writeSteps(skill.steps());
        row.frozen = skill.isFrozen();
        row.frozenAt = skill.frozenAt();
        rows.save(row);
    }

    private static String writeSteps(List<StepSpec> steps) {
        try {
            return JSON.writeValueAsString(steps.stream().map(JpaSkillRepository::toMap).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise skill steps", e);
        }
    }

    private static Map<String, Object> toMap(StepSpec step) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stepId", step.stepId());
        out.put("description", step.description());
        out.put("taskType", step.taskType());
        out.put("mandatory", step.mandatory());
        out.put("producesProse", step.producesProse());
        out.put("preconditions", step.preconditions().stream().map(Enum::name).sorted().toList());
        out.put("postconditions", step.postconditions().stream().map(Enum::name).sorted().toList());
        out.put("guardrails", step.guardrails().stream().map(Enum::name).sorted().toList());
        out.put("onFail", step.onFail().name());
        out.put("maxAttempts", step.maxAttempts());
        out.put("rubricId", step.feedback().rubricId());
        out.put("feedbackKind", step.feedback().kind().name());
        out.put("blocking", step.feedback().blocking());
        return out;
    }

    private static Skill toDomain(SkillRow row) {
        Skill skill = new Skill(
                SkillId.of(row.skillId), row.version, row.name, row.intent, readSteps(row.steps));
        skill.rehydrate(row.frozen, row.frozenAt);
        return skill;
    }

    private static List<StepSpec> readSteps(String json) {
        try {
            return JSON.readValue(json, STEP_LIST).stream()
                    .map(JpaSkillRepository::toStep)
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not read skill steps", e);
        }
    }

    private static StepSpec toStep(Map<String, Object> raw) {
        return new StepSpec(
                (String) raw.get("stepId"),
                (String) raw.get("description"),
                (String) raw.get("taskType"),
                Boolean.TRUE.equals(raw.get("mandatory")),
                Boolean.TRUE.equals(raw.get("producesProse")),
                enums(raw.get("preconditions"), Condition.class),
                enums(raw.get("postconditions"), Condition.class),
                enums(raw.get("guardrails"), Guardrail.class),
                OnFail.valueOf((String) raw.get("onFail")),
                ((Number) raw.get("maxAttempts")).intValue(),
                new FeedbackSpec(
                        (String) raw.get("rubricId"),
                        FeedbackKind.valueOf((String) raw.get("feedbackKind")),
                        Boolean.TRUE.equals(raw.get("blocking"))));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> Set<E> enums(Object raw, Class<E> type) {
        List<String> names = raw == null ? List.of() : (List<String>) raw;
        Set<E> out = EnumSet.noneOf(type);
        names.forEach(name -> out.add(Enum.valueOf(type, name)));
        return out;
    }
}
