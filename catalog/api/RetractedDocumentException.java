package com.atlas.catalog.api;

/**
 * Registration refused because the document was withdrawn.
 *
 * <p>Separate from {@link UnlicensedSourceException} because the remedy is different and so is
 * the authority needed. An unlicensed source becomes licensed when a contract is signed, and the
 * same feed then succeeds unchanged. A retraction is a decision about this specific document,
 * and replaying the feed must never undo it — content is withdrawn because somebody said so, and
 * only somebody saying otherwise should bring it back.
 *
 * <p>The bytes stay in the landing zone either way. Acquisition is expensive and irreversible;
 * refusing to <em>serve</em> content is not a reason to destroy it.
 */
public class RetractedDocumentException extends RuntimeException {

    public RetractedDocumentException(String message) {
        super(message);
    }
}
