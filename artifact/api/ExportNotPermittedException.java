package com.atlas.artifact.api;

/**
 * An export was refused because a source behind the deliverable may not leave the platform.
 *
 * <p>Refused whole rather than exported minus the offending rows. A spreadsheet that quietly
 * lost a citation still shows the figure it supported, and the reader has no way to tell that
 * anything was removed — which is a worse outcome than a refusal naming the document.
 */
public class ExportNotPermittedException extends RuntimeException {

    public ExportNotPermittedException(String message) {
        super(message);
    }
}
