package com.atlas.ingestion.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA row for a source contract.
 *
 * <p>Permitted uses are stored as a comma-separated list of enum names rather than one boolean
 * column per use. Adding a seventh use then needs no migration, and — more importantly — a use the
 * running code does not recognise round-trips instead of being silently dropped, which is what
 * would happen if an unknown name met a fixed set of columns.
 */
@Entity
@Table(name = "source_contract")
class RightsRow {

    @Id
    @Column(name = "source_id", nullable = false, length = 128)
    String sourceId;

    @Column(name = "contract_id", length = 128)
    String contractId;

    @Column(name = "permitted_uses", nullable = false, length = 512)
    String permittedUses;

    @Column(name = "max_quote_chars", nullable = false)
    int maxQuoteChars;

    /** Null means never restricted, which differs from an embargo that has since expired. */
    @Column(name = "embargo_until")
    Instant embargoUntil;

    @Column(name = "redistribution", nullable = false, length = 32)
    String redistribution;

    @Column(name = "retention_policy", length = 128)
    String retentionPolicy;

    protected RightsRow() {
        // required by JPA
    }
}
