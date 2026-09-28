package com.atlas.catalog.adapter.in.web;

import com.atlas.catalog.api.CatalogApi;
import com.atlas.catalog.api.RegisterDocumentCommand;
import com.atlas.catalog.api.RetractedDocumentException;
import com.atlas.catalog.api.UnlicensedSourceException;
import com.atlas.contracts.v1.RegisterDocumentRequest;
import com.atlas.contracts.v1.RetractDocumentRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Driving adapter: the boundary the Python content plane calls after parsing a document. */
@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController {

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser();

    private final CatalogApi catalog;
    private final Clock clock;

    CatalogController(CatalogApi catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    @PostMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    String register(@RequestBody String body) throws InvalidProtocolBufferException {
        RegisterDocumentRequest.Builder request = RegisterDocumentRequest.newBuilder();
        PARSER.merge(body, request);

        return PRINTER.print(CatalogProtoMapper.registered(catalog.register(
                new RegisterDocumentCommand(
                        request.getDocumentId(),
                        request.getSourceId(),
                        request.getDocType(),
                        Instant.parse(request.getPublishedAt()),
                        request.getContentHash(),
                        request.getStorageKey()),
                asOf(request.getAsOf().getInstant()))));
    }

    @PostMapping(value = "/documents/retractions", produces = MediaType.APPLICATION_JSON_VALUE)
    String retract(@RequestBody String body) throws InvalidProtocolBufferException {
        RetractDocumentRequest.Builder request = RetractDocumentRequest.newBuilder();
        PARSER.merge(body, request);

        return PRINTER.print(CatalogProtoMapper.retracted(catalog.retract(
                request.getDocumentId(),
                request.getReason(),
                asOf(request.getAsOf().getInstant()))));
    }

    private Instant asOf(String instant) {
        return instant.isEmpty() ? clock.instant() : Instant.parse(instant);
    }

    /**
     * A refused registration is a 403, not a 400 or a 500.
     *
     * <p>The request was well-formed and the caller is not entitled to make it. Reporting it as a
     * client format error would send an operator looking for a malformed payload; reporting it as
     * a server fault would page someone. What it actually means is "a contract is missing", which
     * is a commercial action, not an engineering one.
     */
    @ExceptionHandler(UnlicensedSourceException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String unlicensed(UnlicensedSourceException e) {
        return e.getMessage();
    }

    /**
     * Re-registering a withdrawn document.
     *
     * <p>409 rather than 403: the request conflicts with a decision already recorded about this
     * document, and the caller cannot fix it by obtaining a contract. Distinguishable in a log,
     * which matters because the two arrive through the same feed and mean different things to
     * whoever is on call.
     */
    @ExceptionHandler(RetractedDocumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String retracted(RetractedDocumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidProtocolBufferException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(Exception e) {
        return e.getMessage();
    }
}
