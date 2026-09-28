package com.atlas.entitlement.domain.model;

/**
 * The axis along which a grant or denial applies.
 *
 * <p>Deliberately coarse and low-cardinality. These values are compiled into an index pre-filter
 * expression that must be cheap to evaluate during an ANN graph traversal; a dimension with
 * millions of distinct values would push the filter back to post-retrieval, which is precisely the
 * failure this design exists to prevent.
 *
 * <p>Fine-grained per-document restrictions are modelled by hashing the source system's ACL into
 * {@link #ACL_HASH} buckets rather than by adding dimensions.
 */
public enum EntitlementDimension {

    /** A specific publisher, e.g. one broker. The unit most licences are negotiated in. */
    SOURCE,

    /** A licence bundle covering many sources, e.g. a research package tier. */
    CONTRACT_GROUP,

    /** Document type, where a licence covers some kinds of output from a source but not others. */
    DOC_TYPE,

    /** A mirrored ACL from the customer's own SharePoint, Box or Drive. */
    ACL_HASH,

    /** Tenant-internal classification, e.g. a deal team's information barrier. */
    SECURITY_LABEL,

    /** External licensed corpus versus tenant-private corpus. */
    CORPUS
}
