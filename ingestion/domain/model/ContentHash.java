package com.atlas.ingestion.domain.model;

import java.util.HexFormat;

/**
 * SHA-256 of the raw acquired bytes. The document's identity, independent of where it came from.
 *
 * <p>Content addressing does two jobs. It makes the landing zone idempotent — re-acquiring the same
 * bytes is a no-op rather than a duplicate — and it collapses exact duplicates, which matter more
 * than they sound: a single press release arrives as a filing, a wire story, dozens of rewrites and
 * a stack of broker notes quoting it verbatim.
 *
 * <p>This only catches byte-identical copies. Near-duplicates need shingling and semantic
 * clustering downstream; both are necessary, and neither substitutes for the other.
 */
public record ContentHash(byte[] value) {

    private static final int SHA256_BYTES = 32;

    public ContentHash {
        if (value == null || value.length != SHA256_BYTES) {
            throw new IllegalArgumentException(
                    "content hash must be %d bytes of SHA-256, was %s"
                            .formatted(SHA256_BYTES, value == null ? "null" : value.length));
        }
        value = value.clone();
    }

    public static ContentHash ofHex(String hex) {
        return new ContentHash(HexFormat.of().parseHex(hex));
    }

    public String hex() {
        return HexFormat.of().formatHex(value);
    }

    /**
     * Object-store key derived from the hash, fanned out over two levels.
     *
     * <p>A flat prefix would put every object in one partition and throttle the batch plane during
     * backfill, which runs at a hundred times steady-state rate.
     */
    public String storageKey() {
        String hex = hex();
        return "%s/%s/%s".formatted(hex.substring(0, 2), hex.substring(2, 4), hex);
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContentHash c && java.util.Arrays.equals(value, c.value);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return hex();
    }
}
