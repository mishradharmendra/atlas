package com.atlas.knowledge.domain.model;

/**
 * Who or what produced an extraction, and which family of model it belongs to.
 *
 * <p>The family is carried separately from the model name because it is the field the verification
 * rule turns on, and deriving it from the name by prefix matching would break the moment a vendor
 * renamed something. {@code gpt-4o} and {@code gpt-4o-mini} are different models and the same
 * family; that is exactly the case the rule exists to catch.
 */
public record ExtractorId(String model, String family) {

    /** The family reserved for people. A human reviewer breaks any correlation with a model. */
    public static final String HUMAN_FAMILY = "human";

    public ExtractorId {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("extractor must name its model");
        }
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException(
                    "extractor must name its model family; without it two checkpoints of the same "
                            + "base model count as independent verification, which is the failure "
                            + "this type exists to prevent");
        }
    }

    public static ExtractorId model(String model, String family) {
        return new ExtractorId(model, family);
    }

    public static ExtractorId human(String reviewer) {
        return new ExtractorId(reviewer, HUMAN_FAMILY);
    }

    public boolean sharesFamilyWith(ExtractorId other) {
        return family.equals(other.family);
    }

    @Override
    public String toString() {
        return "%s/%s".formatted(family, model);
    }
}
