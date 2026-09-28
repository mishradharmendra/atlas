package com.atlas.catalog.api;

/**
 * Registration refused because the source's contract does not cover it.
 *
 * <p>A distinct type rather than a generic argument error because the caller's correct response is
 * different: the bytes are already in the landing zone and must stay there — losing acquired
 * content is unrecoverable — but nothing downstream may process them until a contract exists.
 */
public class UnlicensedSourceException extends RuntimeException {

    public UnlicensedSourceException(String message) {
        super(message);
    }
}
