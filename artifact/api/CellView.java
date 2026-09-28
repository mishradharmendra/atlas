package com.atlas.artifact.api;

import java.time.Instant;

/**
 * One cell, in the shape a grid or a spreadsheet renders.
 *
 * <p>Carries the machine value and the override side by side rather than a single resolved
 * number. A client handed only the resolved value cannot draw the thing that matters — that the
 * source now disagrees with the correction standing on top of it — and every surface would have
 * to re-derive it, which means each of them would get it slightly differently.
 */
public record CellView(
        String cellRef,
        String presentedValue,
        String machineValue,
        String overrideValue,
        String overriddenBy,
        Instant overriddenAt,
        String overrideRationale,
        boolean diverges,
        String derivedFrom,
        String docId,
        String spanId,

        /**
         * Where the quote sits in the document.
         *
         * <p>Omitted until 2026-09-23, which left a client holding a span id and no way to act
         * on it: the citation could be displayed but not followed. Coordinates are the whole
         * reason {@code SpanRef} carries more than an identifier.
         */
        int page,
        int charStart,
        int charEnd,
        String quotedText,
        String authority,
        Instant publishedAt,
        String sourceName) {}
