package com.atlas.shared.provenance;

/**
 * Normalised page coordinates of a span, in the unit square.
 *
 * <p>Normalised rather than absolute so a citation still highlights correctly when the viewer
 * renders the page at a different zoom or DPI than the parser saw.
 */
public record BoundingBox(float x0, float y0, float x1, float y1) {

    public BoundingBox {
        if (x0 < 0 || y0 < 0 || x1 > 1 || y1 > 1) {
            throw new IllegalArgumentException(
                    "bounding box must be normalised to [0,1], was (%f,%f)-(%f,%f)"
                            .formatted(x0, y0, x1, y1));
        }
        if (x1 < x0 || y1 < y0) {
            throw new IllegalArgumentException("bounding box has inverted coordinates");
        }
    }

    /** For spans with no visual geometry — plain-text sources, transcripts, API payloads. */
    public static BoundingBox none() {
        return new BoundingBox(0, 0, 0, 0);
    }
}
