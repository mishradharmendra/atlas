package com.atlas.shared.provenance;

/**
 * Source credibility ranking, assigned at ingest.
 *
 * <p>This exists because embedding distance is indifferent to authority. An earnings call
 * transcript, a sell-side preview note, a news article and a 10-Q can score almost identically on
 * cosine similarity while differing enormously in what a professional would accept as evidence.
 * Without an explicit tier, a ranker has no way to prefer the filing over the blog post that
 * paraphrased it.
 *
 * <p>It is assigned at ingest rather than derived at query time for two reasons: the ranker needs
 * it as a feature on every request, and evaluation rubrics score it as an axis — roughly 40% of
 * positive rubric weight targets temporality, authority and breadth combined.
 */
public enum AuthorityTier {

    /** Filings, earnings and expert-call transcripts, regulator originals. The issuer speaking. */
    PRIMARY(3),

    /** Broker, credit and consultancy research. Expert interpretation of primary material. */
    SECONDARY(2),

    /** News, web, generative summaries. Usually restating one of the above. */
    TERTIARY(1);

    private final int rank;

    AuthorityTier(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean outranks(AuthorityTier other) {
        return rank > other.rank;
    }
}
