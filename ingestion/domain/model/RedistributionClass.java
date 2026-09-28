package com.atlas.ingestion.domain.model;

/**
 * How widely content from a source may travel.
 *
 * <p>Orthogonal to {@link PermittedUse}: that governs what the platform may compute, this governs
 * who the results may reach. A source can permit embedding while forbidding redistribution beyond
 * the licensing firm.
 */
public enum RedistributionClass {

    /** No restriction. Regulatory filings, public registries. */
    PUBLIC,

    /** Any licensed tenant may see it. The common case for a research bundle. */
    LICENSED_BROAD,

    /**
     * Only tenants who are already clients of the publisher.
     *
     * <p>The restriction that makes entitlement per-principal rather than per-platform: a broker
     * will grant access to its own clients and nobody else, so the same corpus resolves differently
     * for two tenants on the same deployment.
     */
    LICENSED_CLIENTS_ONLY,

    /** The customer's own material. Never leaves the tenant boundary, never trains a model. */
    TENANT_PRIVATE
}
