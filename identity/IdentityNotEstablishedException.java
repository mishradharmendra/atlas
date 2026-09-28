package com.atlas.identity;

/** Raised when a presented token is absent, malformed, expired, or from an issuer we do not trust. */
public class IdentityNotEstablishedException extends RuntimeException {

    public IdentityNotEstablishedException(String message) {
        super(message);
    }

    public IdentityNotEstablishedException(String message, Throwable cause) {
        super(message, cause);
    }
}
