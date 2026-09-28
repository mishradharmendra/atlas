package com.atlas.ingestion.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a licence allows the platform to <em>do</em> with a document, as distinct from who may see
 * it.
 *
 * <p>Seeing is {@code entitlement}'s concern. This is the other half: a principal may be perfectly
 * entitled to read a broker note while the platform is still forbidden from embedding it.
 *
 * <p>Immutable. Revoking a use returns a new instance, so the permission set in force when a
 * derived artefact was produced can be reconstructed from the audit trail rather than inferred from
 * the current state.
 */
public final class PermittedUse {

    /** Contractual quotation ceilings sit far below anything fair-use would permit. */
    public static final int DEFAULT_MAX_QUOTE_CHARS = 2_000;

    private final Set<Use> uses;
    private final int maxQuoteChars;

    private PermittedUse(Set<Use> uses, int maxQuoteChars) {
        if (maxQuoteChars < 0) {
            throw new IllegalArgumentException("quote ceiling must not be negative");
        }
        if (uses.contains(Use.QUOTE_VERBATIM) && maxQuoteChars == 0) {
            throw new IllegalArgumentException(
                    "QUOTE_VERBATIM granted with a zero-character ceiling is a contradiction; "
                            + "either grant a ceiling or withhold the permission");
        }
        this.uses = Collections.unmodifiableSet(EnumSet.copyOf(uses.isEmpty() ? EnumSet.noneOf(Use.class) : uses));
        this.maxQuoteChars = maxQuoteChars;
    }

    /**
     * Nothing is permitted.
     *
     * <p>The default, and the value used when a source contract has not yet been recorded. Failing
     * closed here means an unlicensed document is inert rather than quietly indexed: the pipeline
     * skips it and says why, instead of processing it and creating an exposure nobody sees until an
     * audit.
     */
    public static PermittedUse none() {
        return new PermittedUse(EnumSet.noneOf(Use.class), 0);
    }

    public static PermittedUse of(int maxQuoteChars, Use... granted) {
        var set = EnumSet.noneOf(Use.class);
        Collections.addAll(set, granted);
        return new PermittedUse(set, maxQuoteChars);
    }

    /** Public-domain material: everything except training, which stays an explicit decision. */
    public static PermittedUse forPublicDomain() {
        return of(
                DEFAULT_MAX_QUOTE_CHARS,
                Use.INDEX_LEXICAL,
                Use.INDEX_DENSE,
                Use.EXTRACT_TO_GRAPH,
                Use.QUOTE_VERBATIM,
                Use.EXPORT_TO_ARTIFACT);
    }

    /**
     * A customer's own documents.
     *
     * <p>Full platform capability, and {@link Use#TRAIN_MODELS} permanently absent —
     * {@link #grant} refuses to add it back. The commitment that customer material is never used
     * for training is one customers verify in security review, so it is enforced by the type rather
     * than by a configuration flag somebody can set.
     */
    public static PermittedUse forTenantContent() {
        return of(
                DEFAULT_MAX_QUOTE_CHARS,
                Use.INDEX_LEXICAL,
                Use.INDEX_DENSE,
                Use.EXTRACT_TO_GRAPH,
                Use.QUOTE_VERBATIM,
                Use.EXPORT_TO_ARTIFACT);
    }

    public boolean permits(Use use) {
        return uses.contains(use);
    }

    public Set<Use> uses() {
        return uses;
    }

    public int maxQuoteChars() {
        return maxQuoteChars;
    }

    /**
     * Withdraw one permission, leaving the rest intact.
     *
     * <p>The operation a licence renegotiation actually produces. Everything derived under the
     * withdrawn use must then be re-derived or purged, which is only possible because each derived
     * artefact records the source it came from.
     */
    public PermittedUse revoke(Use use) {
        if (!uses.contains(use)) {
            return this;
        }
        var remaining = EnumSet.copyOf(uses);
        remaining.remove(use);
        int ceiling = use == Use.QUOTE_VERBATIM ? 0 : maxQuoteChars;
        return new PermittedUse(remaining, ceiling);
    }

    public PermittedUse grant(Use use, int quoteCeiling) {
        if (use == Use.TRAIN_MODELS) {
            throw new IllegalArgumentException(
                    "training permission cannot be granted through this API; it requires an "
                            + "explicit, separately audited contract amendment");
        }
        var granted = EnumSet.copyOf(uses);
        granted.add(use);
        return new PermittedUse(granted, use == Use.QUOTE_VERBATIM ? quoteCeiling : maxQuoteChars);
    }

    /** Truncation ceiling for a verbatim quote, or zero when quoting is not permitted at all. */
    public int quoteCeiling() {
        return permits(Use.QUOTE_VERBATIM) ? maxQuoteChars : 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PermittedUse p
                && uses.equals(p.uses)
                && maxQuoteChars == p.maxQuoteChars;
    }

    @Override
    public int hashCode() {
        return uses.hashCode() * 31 + maxQuoteChars;
    }

    @Override
    public String toString() {
        return "PermittedUse" + uses + "@" + maxQuoteChars;
    }
}
