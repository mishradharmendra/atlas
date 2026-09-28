package com.atlas.ingestion.api;

import java.time.Instant;
import java.util.Set;

/**
 * A contract being registered against a source.
 *
 * <p>Uses are named as strings at this boundary so that other modules and the Python plane do not
 * depend on the {@code Use} enum's internals; the application service resolves them and refuses
 * anything it does not recognise. An unrecognised permission silently dropped is a permission the
 * platform believes it has.
 *
 * @param embargoUntil null means no embargo, which is different from an embargo in the past: the
 *     first is "never restricted", the second is "was restricted and is not any more". The audit
 *     trail needs both.
 */
public record SourceContractCommand(
        String sourceId,
        String contractId,
        Set<String> permittedUses,
        int maxQuoteChars,
        Instant embargoUntil,
        String redistribution,
        String retentionPolicy) {}
