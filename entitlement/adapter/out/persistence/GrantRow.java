package com.atlas.entitlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA row for a grant.
 *
 * <p>A separate type from {@code Grant}, not an annotated version of it. JPA needs a no-arg
 * constructor and mutable fields, which is precisely the opposite of a value object that cannot be
 * constructed in an invalid state. Annotating the domain type would trade every invariant in it
 * for the convenience of skipping this class.
 */
@Entity
@Table(name = "entitlement_grant")
class GrantRow {

    @Id
    @Column(name = "grant_id", nullable = false)
    UUID grantId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    /** Null means the grant applies to every principal in the tenant. */
    @Column(name = "principal_id")
    UUID principalId;

    @Column(name = "dimension", nullable = false, length = 32)
    String dimension;

    @Column(name = "grant_value", nullable = false, length = 256)
    String grantValue;

    @Column(name = "effect", nullable = false, length = 8)
    String effect;

    @Column(name = "effective_from")
    Instant effectiveFrom;

    @Column(name = "effective_to")
    Instant effectiveTo;

    @Column(name = "contract_id", length = 128)
    String contractId;

    protected GrantRow() {
        // required by JPA
    }
}
