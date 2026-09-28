package com.atlas.shared.replay;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Everything that can change an answer, in one comparable value.
 *
 * <h2>Why every cache key carries this</h2>
 *
 * <p>An agent caches aggressively — retrieval results, plans, sub-answers — because it must. The
 * failure that follows is specific and very hard to find: a cache key built from the question
 * alone keeps serving yesterday's answer after the model was upgraded, the prompt was rewritten,
 * or the index was rebuilt. Nothing errors. The evaluation run that was supposed to measure the
 * improvement measures the cache instead, reports no change, and the improvement is reverted as
 * ineffective.
 *
 * <p>So the fingerprint is not an audit nicety bolted on for compliance. It is the thing that
 * makes an experiment mean anything, and it belongs in the key rather than beside it.
 *
 * <h2>Why a sorted map and not a concatenated string</h2>
 *
 * <p>Two runs with the same components in a different order must produce the same fingerprint, or
 * every cache miss looks like a configuration change. Sorting is what makes the value a function
 * of the configuration rather than of the order someone happened to write it in.
 */
public record ConfigFingerprint(SortedMap<String, String> components) implements Comparable<ConfigFingerprint> {

    /** The components a run must always pin. Absent any of them, a replay is not reproducible. */
    public static final String MODEL = "model";

    public static final String PROMPT_VERSION = "prompt_version";
    public static final String INDEX_VERSION = "index_version";
    public static final String RETRIEVAL_PARAMS = "retrieval_params";

    public ConfigFingerprint {
        Objects.requireNonNull(components, "components");
        components = new TreeMap<>(components);

        for (String required : new String[] {MODEL, PROMPT_VERSION, INDEX_VERSION, RETRIEVAL_PARAMS}) {
            String value = components.get(required);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        ("a config fingerprint must pin '%s'. Leaving it out means a change to it "
                                        + "does not invalidate caches, so the next evaluation run "
                                        + "measures the cache instead of the change")
                                .formatted(required));
            }
        }
    }

    public static ConfigFingerprint of(
            String model, String promptVersion, String indexVersion, String retrievalParams) {
        SortedMap<String, String> components = new TreeMap<>();
        components.put(MODEL, model);
        components.put(PROMPT_VERSION, promptVersion);
        components.put(INDEX_VERSION, indexVersion);
        components.put(RETRIEVAL_PARAMS, retrievalParams);
        return new ConfigFingerprint(components);
    }

    /** An additional component — a skill version, a reranker — folded into the same value. */
    public ConfigFingerprint with(String name, String value) {
        SortedMap<String, String> extended = new TreeMap<>(components);
        extended.put(name, value);
        return new ConfigFingerprint(extended);
    }

    /**
     * The cache key for a question under this configuration.
     *
     * <p>There is deliberately no method that produces a key from the question alone.
     */
    public String cacheKey(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("cache key needs a question");
        }
        return "%s|%s".formatted(Integer.toHexString(question.hashCode()), value());
    }

    /** The canonical rendering. Stable across runs, JVMs and orderings. */
    public String value() {
        StringBuilder out = new StringBuilder();
        components.forEach((name, component) -> {
            if (!out.isEmpty()) {
                out.append(';');
            }
            out.append(name).append('=').append(component);
        });
        return out.toString();
    }

    /**
     * What changed between two configurations.
     *
     * <p>The question asked when a metric moves and nobody knows why. A boolean "the fingerprint
     * differs" sends someone diffing config files by hand.
     */
    public SortedMap<String, String> differenceFrom(ConfigFingerprint other) {
        SortedMap<String, String> changed = new TreeMap<>();
        components.forEach((name, mine) -> {
            String theirs = other.components.get(name);
            if (!mine.equals(theirs)) {
                changed.put(name, "%s -> %s".formatted(theirs == null ? "absent" : theirs, mine));
            }
        });
        other.components.keySet().stream()
                .filter(name -> !components.containsKey(name))
                .forEach(name -> changed.put(name, "%s -> absent".formatted(other.components.get(name))));
        return changed;
    }

    @Override
    public int compareTo(ConfigFingerprint other) {
        return value().compareTo(other.value());
    }

    @Override
    public String toString() {
        return value();
    }
}
