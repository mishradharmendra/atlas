package com.atlas.memory.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Something a run learned, and the conditions under which it may be repeated.
 *
 * <p>Immutable except for withdrawal. An entry that could be edited would let a later run
 * rewrite what an earlier one established, and the trace would still name the earlier run as
 * the author.
 */
public class MemoryEntry {

    private final String entryId;
    private final String tenantId;
    private final MemoryKind kind;
    private final String text;
    private final String writtenByRun;
    private final Instant writtenAt;
    private final Set<String> sourceDocIds;
    private final Set<String> entitlementTags;
    private final Instant expiresAt;

    private boolean withdrawn;
    private String withdrawnReason;

    public MemoryEntry(
            String entryId,
            String tenantId,
            MemoryKind kind,
            String text,
            String writtenByRun,
            Instant writtenAt,
            Set<String> sourceDocIds,
            Set<String> entitlementTags,
            Instant expiresAt) {

        this.entryId = require(entryId, "entry id");
        this.tenantId = require(tenantId, "tenant id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.text = require(text, "text");
        this.writtenByRun = require(writtenByRun, "written-by run");
        this.writtenAt = Objects.requireNonNull(writtenAt, "written-at");
        this.sourceDocIds = Set.copyOf(Objects.requireNonNullElseGet(sourceDocIds, Set::of));
        this.entitlementTags = Set.copyOf(Objects.requireNonNullElseGet(entitlementTags, Set::of));
        this.expiresAt = expiresAt;

        if (kind.requiresSource() && this.sourceDocIds.isEmpty()) {
            throw new IllegalArgumentException(
                    ("a remembered fact must name the document it came from; without it the "
                                    + "takedown cascade cannot withdraw it when the source is "
                                    + "retracted, and the agent keeps asserting it from recall"));
        }
        if (this.entitlementTags.isEmpty()) {
            throw new IllegalArgumentException(
                    "a memory entry must carry the entitlement tags it was learned under; an "
                            + "untagged entry is readable by every caller, and the symptom is "
                            + "more results rather than an error");
        }
        if (expiresAt != null && !expiresAt.isAfter(writtenAt)) {
            throw new IllegalArgumentException("an entry cannot expire before it was written");
        }
    }

    /**
     * Whether this entry may be repeated at a moment, by a caller holding these tags.
     *
     * <p>Every condition is a reason to withhold, so they are all checked and the default is no.
     */
    public boolean isReadableAt(Instant moment, String byTenant, Set<String> permittedTags) {
        if (withdrawn || permittedTags == null || permittedTags.isEmpty()) {
            return false;
        }
        if (!tenantId.equals(byTenant)) {
            return false;
        }
        if (expiresAt != null && !moment.isBefore(expiresAt)) {
            return false;
        }
        return permittedTags.containsAll(entitlementTags);
    }

    /**
     * Withdraws the entry because a document behind it was retracted.
     *
     * <p>Withdrawn rather than deleted: "what did the agent believe on Tuesday, and why did it
     * stop" is a question a compliance review asks, and a deleted row cannot answer it. Nothing
     * reads a withdrawn entry, so the effect on behaviour is identical to deletion.
     */
    public void withdraw(String reason) {
        this.withdrawn = true;
        this.withdrawnReason = reason;
    }

    public boolean mentionsAnyOf(Set<String> docIds) {
        return sourceDocIds.stream().anyMatch(docIds::contains);
    }

    public String entryId() {
        return entryId;
    }

    public String tenantId() {
        return tenantId;
    }

    public MemoryKind kind() {
        return kind;
    }

    public String text() {
        return text;
    }

    public String writtenByRun() {
        return writtenByRun;
    }

    public Instant writtenAt() {
        return writtenAt;
    }

    public Set<String> sourceDocIds() {
        return new LinkedHashSet<>(sourceDocIds);
    }

    public Set<String> entitlementTags() {
        return new LinkedHashSet<>(entitlementTags);
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isWithdrawn() {
        return withdrawn;
    }

    public String withdrawnReason() {
        return withdrawnReason;
    }

    /** Rehydration from storage, which must not re-run withdrawal side effects. */
    public void rehydrateWithdrawal(boolean isWithdrawn, String reason) {
        this.withdrawn = isWithdrawn;
        this.withdrawnReason = reason;
    }

    private static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a memory entry needs a " + what);
        }
        return value;
    }
}
