package com.atlas.ingestion.api;

/**
 * Open Host Service for ingestion rights.
 *
 * <p>Exposes the question every downstream derivation must ask — "may we do this with this
 * source's content?" — without exposing how rights are modelled. The catalog asks it before
 * admitting a document; the enrichment plane will ask it before embedding or extracting.
 */
public interface RightsApi {

    /** Records the contract for a source. Re-registering replaces it. */
    void registerContract(SourceContractCommand command);

    /**
     * Whether the source's contract permits a named use.
     *
     * <p>Returns false for an unregistered source rather than throwing, because the caller's
     * correct response is identical in both cases: do not derive. Throwing would tempt callers
     * into a catch block that continues.
     */
    boolean permits(String sourceId, String use);

    /**
     * Whether content from this source may be searched at the given instant.
     *
     * <p>Two conditions, and the second is the one that gets missed: the contract must permit
     * lexical indexing, <em>and</em> any embargo must have expired. An embargo is a time-boxed
     * exclusivity window, so the same document is lawful tomorrow and unlawful today.
     */
    boolean mayBeCatalogued(String sourceId, java.time.Instant at);
}
