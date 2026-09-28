package com.atlas.knowledge.domain.model;

import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.Validity;
import java.util.Locale;
import java.util.Objects;

/**
 * A name an entity was known by, and the window in which that name applied.
 *
 * <p>Names are temporal facts, not attributes. "Facebook" resolves to Meta in a 2018 filing and to
 * nothing in a 2024 one; a store that keeps only the current name silently rewrites history, and a
 * store that keeps names without dates cannot tell a rename from an ambiguity.
 */
public record Alias(String value, Validity validity, String assertedBySource) {

    public Alias {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("alias value must not be blank");
        }
        Objects.requireNonNull(validity, "alias validity");
        if (assertedBySource == null || assertedBySource.isBlank()) {
            throw new IllegalArgumentException(
                    "an alias must name the source that asserted it; an unattributed name cannot "
                            + "be retracted when the source is found to be wrong");
        }
    }

    /**
     * The form used for matching: case-folded, punctuation flattened, whitespace collapsed.
     *
     * <p>Legal suffixes are deliberately <em>not</em> stripped. It is the obvious next step and it
     * is wrong: "Apple Inc" and "Apple Corps" both reduce to "apple", which silently merges a
     * computer manufacturer with a record label. Suffix handling belongs in the resolver, where it
     * can produce a candidate set and abstain, rather than here, where it would produce a
     * collision that no downstream code can detect.
     */
    public String normalised() {
        return normalise(value);
    }

    /** The same folding, applied to a name that is not yet an alias — a user's query, say. */
    public static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public boolean appliesAt(AsOf at) {
        return validity.contains(at);
    }
}
