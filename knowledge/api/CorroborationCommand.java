package com.atlas.knowledge.api;

/**
 * A second extractor's independent reading of the span a proposed edge came from.
 *
 * <p>Note what this is not: a boolean. An API of {@code confirm(id, true)} lets the caller do the
 * comparison, and a caller that does the comparison is a caller that can be lazy about it — the
 * common shortcut being to pass {@code true} whenever the second model returned anything at all.
 *
 * <p>By carrying what the second extractor actually read, the comparison happens here, once, where
 * it can be tested. A second extractor that read nothing passes a null type, which is a
 * disagreement and not an absence of one.
 */
public record CorroborationCommand(
        String relationshipId,
        String extractorModel,
        String extractorFamily,
        String assertedRelationshipType,
        String assertedObjectEntityId,
        String note) {

    /** The second extractor did not find the relationship in the span at all. */
    public boolean assertsNothing() {
        return assertedRelationshipType == null || assertedRelationshipType.isBlank();
    }
}
