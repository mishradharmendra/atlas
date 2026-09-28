package com.atlas.ingestion.adapter.in.web;

import com.atlas.contracts.v1.RegisterSourceContractRequest;
import com.atlas.ingestion.api.RightsApi;
import com.atlas.ingestion.api.SourceContractCommand;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter: where a source's contract is recorded before anything from it is catalogued.
 *
 * <p>Acquisition runs ahead of this on purpose. Bytes land in the immutable store unconditionally,
 * because content you have paid for and may not be able to re-acquire is unrecoverable, and only
 * then is permission asked. Until a contract exists here the source grants nothing and its
 * documents are refused by the catalog, which is the pipeline working rather than failing.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
class IngestionController {

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser();

    private final RightsApi rights;
    private final Clock clock;

    IngestionController(RightsApi rights, Clock clock) {
        this.rights = rights;
        this.clock = clock;
    }

    @PostMapping(value = "/contracts", produces = MediaType.APPLICATION_JSON_VALUE)
    String registerContract(@RequestBody String body) throws InvalidProtocolBufferException {
        RegisterSourceContractRequest.Builder builder = RegisterSourceContractRequest.newBuilder();
        PARSER.merge(body, builder);
        RegisterSourceContractRequest request = builder.build();

        Instant at = request.getAsOf().getInstant().isEmpty()
                ? clock.instant()
                : Instant.parse(request.getAsOf().getInstant());

        rights.registerContract(new SourceContractCommand(
                request.getSourceId(),
                request.getContractId(),
                new LinkedHashSet<>(request.getPermittedUsesList()),
                request.getMaxQuoteChars(),
                request.getEmbargoUntil().isEmpty() ? null : Instant.parse(request.getEmbargoUntil()),
                IngestionProtoMapper.redistribution(request.getRedistribution()),
                request.getRetentionPolicy()));

        return PRINTER.print(IngestionProtoMapper.registered(request, at));
    }

    /**
     * An unrecognised permission is a 400, not a silently narrowed grant.
     *
     * <p>Dropping a use the caller asked for would record a contract the platform believes is
     * narrower than the one that was signed, and the divergence would only surface when someone
     * asked why a licensed source is not being indexed.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String rejected(IllegalArgumentException exc) {
        return "{\"error\":\"%s\"}".formatted(exc.getMessage());
    }
}
