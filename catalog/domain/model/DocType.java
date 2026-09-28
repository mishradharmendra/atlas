package com.atlas.catalog.domain.model;

import com.atlas.shared.provenance.AuthorityTier;
import java.time.Duration;

/**
 * What kind of document this is, in the domain's terms rather than the file system's.
 *
 * <p>The distinction matters because a pitch deck, an investment committee memo, an analyst
 * initiation note and a marketing brochure are entirely different things that all arrive as PDFs. A
 * generic connector hands all four to a model as "a PDF" and leaves it to work out the conventions
 * at query time, which is both expensive and unreliable.
 *
 * <p>Each type carries two ranking inputs that must be known before a query arrives:
 *
 * <ul>
 *   <li><b>Authority</b> — whether this is the issuer speaking, an expert interpreting, or a
 *       reporter restating.
 *   <li><b>Shelf life</b> — how fast its currency decays. A news item is stale in days; an annual
 *       filing stays authoritative for a quarter. This is what makes "recent" mean different things
 *       for different sources, and why a fresh news item does not substitute for the latest filing.
 * </ul>
 */
public enum DocType {
    ANNUAL_REPORT("10-K", AuthorityTier.PRIMARY, Duration.ofDays(365)),
    QUARTERLY_REPORT("10-Q", AuthorityTier.PRIMARY, Duration.ofDays(90)),
    CURRENT_REPORT("8-K", AuthorityTier.PRIMARY, Duration.ofDays(90)),
    PROSPECTUS("prospectus", AuthorityTier.PRIMARY, Duration.ofDays(365)),
    EARNINGS_TRANSCRIPT("earnings_transcript", AuthorityTier.PRIMARY, Duration.ofDays(90)),
    CONFERENCE_TRANSCRIPT("conference_transcript", AuthorityTier.PRIMARY, Duration.ofDays(120)),
    EXPERT_CALL("expert_call", AuthorityTier.PRIMARY, Duration.ofDays(180)),
    INVESTOR_PRESENTATION("investor_presentation", AuthorityTier.PRIMARY, Duration.ofDays(90)),
    PRESS_RELEASE("press_release", AuthorityTier.PRIMARY, Duration.ofDays(30)),

    INITIATION_NOTE("initiation_note", AuthorityTier.SECONDARY, Duration.ofDays(180)),
    PREVIEW_NOTE("preview_note", AuthorityTier.SECONDARY, Duration.ofDays(30)),
    ESTIMATE_CHANGE("estimate_change", AuthorityTier.SECONDARY, Duration.ofDays(30)),
    INDUSTRY_PRIMER("industry_primer", AuthorityTier.SECONDARY, Duration.ofDays(365)),
    CREDIT_RESEARCH("credit_research", AuthorityTier.SECONDARY, Duration.ofDays(180)),

    NEWS("news", AuthorityTier.TERTIARY, Duration.ofDays(7)),
    TRADE_PRESS("trade_press", AuthorityTier.TERTIARY, Duration.ofDays(30)),

    INTERNAL_MEMO("internal_memo", AuthorityTier.SECONDARY, Duration.ofDays(365)),
    BOARD_DECK("board_deck", AuthorityTier.SECONDARY, Duration.ofDays(365)),
    MEETING_NOTE("meeting_note", AuthorityTier.SECONDARY, Duration.ofDays(180));

    private final String wireName;
    private final AuthorityTier authority;
    private final Duration shelfLife;

    DocType(String wireName, AuthorityTier authority, Duration shelfLife) {
        this.wireName = wireName;
        this.authority = authority;
        this.shelfLife = shelfLife;
    }

    public String wireName() {
        return wireName;
    }

    public AuthorityTier authority() {
        return authority;
    }

    public Duration shelfLife() {
        return shelfLife;
    }

    /** Whether a document of this type published {@code age} ago still counts as current. */
    public boolean isCurrentAfter(Duration age) {
        return age.compareTo(shelfLife) <= 0;
    }
}
