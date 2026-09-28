package com.atlas.entitlement.domain.model;

import com.atlas.shared.temporal.AsOf;
import java.time.Instant;

/**
 * One grant or denial along a single dimension, optionally time-bounded.
 *
 * <h2>The embargo case</h2>
 *
 * <p>The validity window is what makes embargoed content expressible. A broker note is typically
 * visible to the publishing firm's own clients on release and to a wider audience only after a
 * contractual delay. That is a tag whose {@code validFrom} is in the future for most principals.
 *
 * <p>The window is evaluated against the <em>query's</em> as-of, not against wall-clock time at
 * ingest. A user asking "what did analysts say in May?" from a September vantage point is entitled
 * to May content whose embargo has since lapsed; a user asking a question dated in May is not. Get
 * this backwards and you either leak embargoed material or hide content the customer has paid for.
 */
public record EntitlementTag(
        EntitlementDimension dimension, String value, Instant validFrom, Instant validTo) {

    public EntitlementTag {
        if (dimension == null) {
            throw new IllegalArgumentException("entitlement tag must name a dimension");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("entitlement tag must carry a value");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                    "entitlement tag validity ends (%s) before it begins (%s)"
                            .formatted(validTo, validFrom));
        }
    }

    /** An unbounded tag: applies at any as-of. */
    public static EntitlementTag always(EntitlementDimension dimension, String value) {
        return new EntitlementTag(dimension, value, null, null);
    }

    /** A tag that only takes effect once an embargo has lapsed. */
    public static EntitlementTag from(EntitlementDimension dimension, String value, Instant validFrom) {
        return new EntitlementTag(dimension, value, validFrom, null);
    }

    /** Whether this tag is in force at the instant the question is being asked about. */
    public boolean appliesAt(AsOf queryAsOf) {
        Instant at = queryAsOf.instant();
        if (validFrom != null && at.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !at.isAfter(validTo);
    }

    /** Matching ignores the validity window; {@link #appliesAt} decides whether it is in force. */
    public boolean matches(EntitlementDimension otherDimension, String otherValue) {
        return dimension == otherDimension && value.equals(otherValue);
    }
}
