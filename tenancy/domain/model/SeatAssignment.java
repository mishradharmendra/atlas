package com.atlas.tenancy.domain.model;

import com.atlas.shared.identity.PrincipalId;
import com.atlas.shared.temporal.AsOf;
import com.atlas.shared.temporal.Validity;
import java.util.Objects;

/**
 * A seat held by a principal over a window.
 *
 * <p>Temporal because "who held a seat in September" is a billable fact and staff move. An
 * assignment table recording only the present cannot answer it, and cannot show that a departed
 * employee's access ended when their seat was reassigned — which is the question an auditor asks.
 */
public record SeatAssignment(PrincipalId principal, Validity validity) {

    public SeatAssignment {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(validity, "assignment validity");
    }

    public boolean heldAt(AsOf at) {
        return validity.contains(at);
    }
}
