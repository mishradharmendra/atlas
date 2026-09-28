package com.atlas.tenancy.domain.port;

import com.atlas.shared.identity.TenantId;
import com.atlas.tenancy.domain.model.Tenant;
import java.util.Optional;

/** Durable store for tenants, their subscriptions and their seats. */
public interface TenantRepository {

    Optional<Tenant> findById(TenantId id);

    void save(Tenant tenant);
}
