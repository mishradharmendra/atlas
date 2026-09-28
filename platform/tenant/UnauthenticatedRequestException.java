package com.atlas.platform.tenant;

/** Raised when a request reaches tenant-scoped work without a verified identity. */
public class UnauthenticatedRequestException extends RuntimeException {

    public UnauthenticatedRequestException(String message) {
        super(message);
    }
}
