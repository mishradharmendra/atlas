package com.atlas.ingestion.application;

import com.atlas.ingestion.api.RightsApi;
import com.atlas.ingestion.api.SourceContractCommand;
import com.atlas.ingestion.domain.model.PermittedUse;
import com.atlas.ingestion.domain.model.RedistributionClass;
import com.atlas.ingestion.domain.model.RightsMetadata;
import com.atlas.ingestion.domain.model.Use;
import com.atlas.ingestion.domain.port.RightsRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers source contracts and answers permission questions from them. */
@Service
class RightsService implements RightsApi {

    private final RightsRepository repository;

    RightsService(RightsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void registerContract(SourceContractCommand command) {
        PermittedUse permitted = PermittedUse.of(
                command.maxQuoteChars() > 0
                        ? command.maxQuoteChars()
                        : PermittedUse.DEFAULT_MAX_QUOTE_CHARS,
                command.permittedUses().stream().map(RightsService::use).toArray(Use[]::new));

        repository.save(new RightsMetadata(
                command.sourceId(),
                command.contractId(),
                permitted,
                command.embargoUntil(),
                RedistributionClass.valueOf(command.redistribution()),
                command.retentionPolicy()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean permits(String sourceId, String use) {
        return rights(sourceId).permittedUse().permits(use(use));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayBeCatalogued(String sourceId, Instant at) {
        RightsMetadata rights = rights(sourceId);
        if (!rights.permittedUse().permits(Use.INDEX_LEXICAL)) {
            return false;
        }
        // An embargo is a time-boxed exclusivity window, so the same document is unlawful today
        // and lawful tomorrow. Evaluated against the supplied instant rather than the clock so
        // that a backfill replaying last quarter is judged by last quarter's rules.
        return rights.embargoUntil() == null || !at.isBefore(rights.embargoUntil());
    }

    /** An unregistered source grants nothing, rather than being absent and therefore unchecked. */
    private RightsMetadata rights(String sourceId) {
        return repository
                .findBySourceId(sourceId)
                .orElseGet(() -> RightsMetadata.forUnlicensedSource(sourceId));
    }

    private static Use use(String name) {
        return Optional.ofNullable(name)
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .map(n -> {
                    try {
                        return Use.valueOf(n);
                    } catch (IllegalArgumentException e) {
                        // A permission the platform cannot name is a permission it must not
                        // assume. Dropping it silently would record a narrower contract than was
                        // signed; accepting it blindly would record a wider one.
                        throw new IllegalArgumentException(
                                "unknown permitted use '" + n + "'; contract not registered", e);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("permitted use must be named"));
    }
}
