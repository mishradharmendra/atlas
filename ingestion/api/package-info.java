/**
 * The public interface of the ingestion context.
 *
 * <p>Other modules ask this package whether a derivation is permitted. Everything behind it —
 * how rights are stored, how each vendor's feed is translated — is internal and enforced as such
 * by Spring Modulith.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.ingestion.api;
