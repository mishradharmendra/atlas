package com.atlas.shared.temporal;

import java.time.LocalDate;

/**
 * A reporting period with the fiscal arithmetic already resolved.
 *
 * <p>"2Q26" spans different calendar dates for different companies. Apple's fiscal year ends in
 * September; most retailers end in January or February. A model asked to compare "2Q26 same-store
 * sales" across seven tickers will quietly line up periods that are months apart unless something
 * upstream has resolved each label against that entity's own calendar.
 *
 * <p>Resolving it here rather than inside a prompt is what makes peer comparison legitimate, and
 * it is cheaper: the resolution is deterministic, cacheable and testable, which none of those
 * three adjectives apply to a model doing date arithmetic in prose.
 */
public record FiscalPeriod(
        String label, LocalDate startDate, LocalDate endDate, int fiscalYear, int fiscalQuarter) {

    public FiscalPeriod {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("fiscal period label must not be blank");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("fiscal period must be resolved to concrete dates");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "fiscal period end %s precedes start %s".formatted(endDate, startDate));
        }
        if (fiscalQuarter < 0 || fiscalQuarter > 4) {
            throw new IllegalArgumentException(
                    "fiscal quarter must be 0 (full year) or 1-4, was " + fiscalQuarter);
        }
    }

    /** A full financial year. Quarter 0 encodes "not a quarter". */
    public static FiscalPeriod fullYear(String label, int fiscalYear, LocalDate start, LocalDate end) {
        return new FiscalPeriod(label, start, end, fiscalYear, 0);
    }

    public boolean isFullYear() {
        return fiscalQuarter == 0;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Whether two periods may legitimately be compared. Periods from different entities with
     * different calendars can carry the same label and still be months apart, so the label alone
     * is never sufficient evidence that a comparison is like-for-like.
     */
    public boolean isComparableTo(FiscalPeriod other) {
        return fiscalQuarter == other.fiscalQuarter
                && startDate.equals(other.startDate)
                && endDate.equals(other.endDate);
    }
}
