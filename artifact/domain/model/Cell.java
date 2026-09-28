package com.atlas.artifact.domain.model;

import com.atlas.shared.provenance.Citation;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One bound value in a deliverable: what the system computed, what a person corrected it to, and
 * the evidence behind both.
 *
 * <h2>Why the override lives beside the machine value rather than replacing it</h2>
 *
 * <p>Replacing it loses the thing worth knowing. Once both are kept, a refresh can say "the
 * filing now reads 412, you told me 388" — which is a prompt to look again. With only the
 * override stored, the refresh has nothing to compare against and the divergence is invisible;
 * with only the machine value stored, the correction is silently destroyed.
 *
 * <h2>Why a citation is mandatory unless the cell is derived</h2>
 *
 * <p>A naked number in a client deliverable cannot be defended. The exception is a computed cell
 * — a sum, a margin — whose defensibility comes from the cells it was computed from, which carry
 * their own citations. Those record {@code derivedFrom} instead, so the chain still terminates in
 * evidence.
 */
public record Cell(
        String cellRef,
        String machineValue,
        Citation evidence,
        String derivedFrom,
        String overrideValue,
        String overriddenBy,
        Instant overriddenAt,
        String overrideRationale,
        /**
         * What the machine said at the moment the correction was made.
         *
         * <p>Without it, divergence cannot be expressed. A correction always differs from the
         * value it corrects, so comparing the override against the current machine value marks
         * every corrected cell as diverging the instant it is corrected -- and the state that is
         * supposed to mean "the filing has moved under your correction" becomes a synonym for
         * "corrected", which is a signal nobody can act on.
         */
        String machineValueWhenOverridden) {

    public Cell {
        if (cellRef == null || cellRef.isBlank()) {
            throw new IllegalArgumentException("a cell must name its position in the artifact");
        }
        if (evidence == null && (derivedFrom == null || derivedFrom.isBlank())) {
            throw new IllegalArgumentException(
                    ("cell %s has neither a citation nor a derivation. A naked number in a client "
                                    + "deliverable cannot be defended when someone asks where it "
                                    + "came from")
                            .formatted(cellRef));
        }
        if (overrideValue != null && (overriddenBy == null || overriddenBy.isBlank())) {
            throw new IllegalArgumentException(
                    ("cell %s is overridden by nobody. An anonymous correction cannot be audited, "
                                    + "and the first question about a changed number is who "
                                    + "changed it")
                            .formatted(cellRef));
        }
        if (overrideValue != null && (overrideRationale == null || overrideRationale.isBlank())) {
            throw new IllegalArgumentException(
                    ("cell %s is overridden with no reason given. Six months later the reason is "
                                    + "the only thing that distinguishes a correction from a "
                                    + "mistake")
                            .formatted(cellRef));
        }
    }

    /** A cell the system produced from a cited span. */
    public static Cell extracted(String cellRef, String value, Citation evidence) {
        return new Cell(
                cellRef, value, Objects.requireNonNull(evidence), null, null, null, null, null, null);
    }

    /** A cell computed from other cells, which carry the citations. */
    public static Cell derived(String cellRef, String value, String formula) {
        return new Cell(cellRef, value, null, formula, null, null, null, null, null);
    }

    /**
     * Records a correction. The machine value is untouched.
     *
     * <p>Overriding an already-overridden cell replaces the previous correction, which is
     * ordinary editing by the same kind of actor — the machine value, the thing a refresh would
     * otherwise destroy, is still preserved.
     */
    public Cell overriddenTo(String value, String by, Instant at, String rationale) {
        return new Cell(
                cellRef, machineValue, evidence, derivedFrom, value, by, at, rationale, machineValue);
    }

    /**
     * Recomputes the machine value, leaving any override in place.
     *
     * <p>The whole point of the type. A refresh that returned a fresh cell would silently drop
     * the correction, which is the failure that loses a user permanently.
     */
    public Cell refreshedTo(String newMachineValue, Citation newEvidence) {
        return new Cell(
                cellRef,
                newMachineValue,
                newEvidence,
                derivedFrom,
                overrideValue,
                overriddenBy,
                overriddenAt,
                overrideRationale,
                // Deliberately carried through unchanged: it is the reading the analyst was
                // looking at, and a refresh is exactly when it stops matching.
                machineValueWhenOverridden);
    }

    /** What a reader sees: the correction if there is one, otherwise what the system computed. */
    public String presentedValue() {
        return overrideValue != null ? overrideValue : machineValue;
    }

    public boolean isOverridden() {
        return overrideValue != null;
    }

    /**
     * Whether the source has moved since the correction was made and still disagrees with it.
     *
     * <p>The signal a refresh produces, and the cell to look at first. Not an error — the analyst
     * may still be right and the filing restated — but somebody has to decide which.
     *
     * <p>Both halves are load-bearing. Comparing the override with the current machine value
     * alone is true of every correction the instant it is made, so the state would mean
     * "corrected" and a genuine restatement would look like everything else. Asking only whether
     * the source moved would flag the case where it moved *to* the corrected value, which is the
     * source agreeing with the analyst and needs nobody's attention.
     */
    public boolean divergesFromSource() {
        return isOverridden()
                && machineValueWhenOverridden != null
                && !machineValueWhenOverridden.equals(machineValue)
                && !overrideValue.equals(machineValue);
    }

    public Optional<String> sourceDocId() {
        return Optional.ofNullable(evidence).map(citation -> citation.span().docId());
    }
}
