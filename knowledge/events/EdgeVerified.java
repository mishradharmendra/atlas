package com.atlas.knowledge.events;

import java.time.Instant;

/**
 * An edge has cleared two-model verification and is now traversable.
 *
 * <p>Carries both extractor identities because the downstream question is not "was it verified"
 * but "by what" — a quality report that cannot tell a human confirmation from two models agreeing
 * is reporting one number for two very different things.
 */
public record EdgeVerified(
        String relationshipId,
        String subjectId,
        String relationshipType,
        String objectId,
        String firstExtractor,
        String secondExtractor,
        Instant at) {}
