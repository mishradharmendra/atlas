package com.atlas.artifact.application;

import com.atlas.artifact.api.ArtifactDetailView;
import com.atlas.artifact.api.CellView;
import com.atlas.artifact.api.ExportNotPermittedException;
import com.atlas.catalog.api.CatalogApi;
import com.atlas.ingestion.api.RightsApi;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * A deliverable as a spreadsheet, if its sources allow it to leave.
 *
 * <p>The analyst's next action after reading an answer is putting it in a memo, and until this
 * existed the only way out of the platform was retyping. That is not a missing convenience: a
 * retyped figure has no citation attached to it, so the provenance the whole system exists to
 * preserve is lost at precisely the moment the number reaches a client.
 *
 * <p>Two rights are asked about separately because they are different permissions.
 * {@code EXPORT_TO_ARTIFACT} is whether the content may leave at all; {@code QUOTE_VERBATIM} is
 * whether the source's own words may be reproduced. A contract commonly grants the first and
 * limits the second, and a licence that permits derivation but not republication is the normal
 * shape of a market-data agreement.
 */
@Service
public class ArtifactExport {

    static final String WITHHELD = "[quotation not licensed]";

    private static final List<String> HEADER = List.of(
            "artifactStatus",
            "cellRef",
            "value",
            "correctedBy",
            "rationale",
            "divergesFromSource",
            "documentId",
            "sourceName",
            "publishedAt",
            "charStart",
            "charEnd",
            "quotedText");

    private final ArtifactService artifacts;
    private final CatalogApi catalog;
    private final RightsApi rights;

    ArtifactExport(ArtifactService artifacts, CatalogApi catalog, RightsApi rights) {
        this.artifacts = artifacts;
        this.catalog = catalog;
        this.rights = rights;
    }

    /**
     * The deliverable as CSV, or a refusal naming the document that forbids it.
     *
     * <p>The status travels on every row. A CSV has nowhere else to put it, and a draft
     * forwarded to a client reads exactly like a finished one once it is out of the workspace.
     */
    public String csv(String artifactId) {
        ArtifactDetailView artifact = artifacts
                .detail(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("no artifact " + artifactId));

        StringBuilder out = new StringBuilder(row(HEADER));
        for (CellView cell : artifact.cells()) {
            String source = sourceOf(cell.docId());
            if (source != null && !rights.permits(source, "EXPORT_TO_ARTIFACT")) {
                throw new ExportNotPermittedException(
                        ("%s cites %s, whose contract does not permit EXPORT_TO_ARTIFACT. "
                                        + "Remove the cell or obtain the right; the platform will "
                                        + "not export the rest and leave the gap unmarked.")
                                .formatted(artifactId, cell.docId()));
            }
            boolean mayQuote = source == null || rights.permits(source, "QUOTE_VERBATIM");
            out.append(row(List.of(
                    artifact.status(),
                    cell.cellRef(),
                    orEmpty(cell.presentedValue()),
                    orEmpty(cell.overriddenBy()),
                    orEmpty(cell.overrideRationale()),
                    String.valueOf(cell.diverges()),
                    orEmpty(cell.docId()),
                    orEmpty(cell.sourceName()),
                    cell.publishedAt() == null ? "" : cell.publishedAt().toString(),
                    String.valueOf(cell.charStart()),
                    String.valueOf(cell.charEnd()),
                    mayQuote ? orEmpty(cell.quotedText()) : WITHHELD)));
        }
        return out.toString();
    }

    /** Null for a cell with no document behind it — a derived cell has no source to ask about. */
    private String sourceOf(String docId) {
        if (docId == null || docId.isBlank()) {
            return null;
        }
        try {
            return catalog.find(docId).sourceId();
        } catch (NoSuchElementException e) {
            // A cited document the catalog no longer holds. Treated as unlicensed rather than
            // waved through: the safe reading of "I cannot check" is "I may not".
            throw new ExportNotPermittedException(
                    ("cannot export: %s is cited but no longer in the catalog, so its licence "
                                    + "cannot be checked")
                            .formatted(docId));
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String row(List<String> fields) {
        return fields.stream().map(ArtifactExport::escape).reduce((a, b) -> a + "," + b).orElse("")
                + "\r\n";
    }

    /** RFC 4180: a value carrying a comma, a quote or a newline would otherwise shift columns. */
    private static String escape(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0
                && field.indexOf('\r') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
