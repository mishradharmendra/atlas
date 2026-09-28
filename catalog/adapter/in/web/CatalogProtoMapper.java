package com.atlas.catalog.adapter.in.web;

import com.atlas.catalog.api.DocumentView;
import com.atlas.contracts.v1.AuthorityTier;
import com.atlas.contracts.v1.Document;
import com.atlas.contracts.v1.DocumentStatus;
import com.atlas.contracts.v1.RegisterDocumentResponse;
import com.atlas.contracts.v1.RetractDocumentResponse;

/**
 * Anti-corruption layer between the catalog's view type and the published language.
 *
 * <p>Enum names are prefixed on the wire because protobuf enum values share a C++ namespace, so
 * {@code PUBLISHED} would collide with every other enum that wants the word. The mapping is
 * explicit rather than inferred so that an unmappable status fails here, loudly, rather than
 * arriving downstream as {@code UNSPECIFIED} — which a consumer would reasonably read as "not
 * retracted".
 */
final class CatalogProtoMapper {

    private CatalogProtoMapper() {}

    static RegisterDocumentResponse registered(DocumentView view) {
        return RegisterDocumentResponse.newBuilder().setDocument(toProto(view)).build();
    }

    static RetractDocumentResponse retracted(DocumentView view) {
        return RetractDocumentResponse.newBuilder().setDocument(toProto(view)).build();
    }

    static Document toProto(DocumentView view) {
        Document.Builder document = Document.newBuilder()
                .setDocumentId(view.documentId())
                .setSourceId(view.sourceId())
                .setDocType(view.docType())
                .setAuthority(authority(view.authority()))
                .setStatus(status(view.status()))
                .setPublishedAt(view.publishedAt().toString())
                .setContentHash(view.contentHash())
                .setStorageKey(view.storageKey());

        if (view.supersededBy() != null) {
            document.setSupersededBy(view.supersededBy());
        }
        if (view.retractionReason() != null) {
            document.setRetractionReason(view.retractionReason());
        }
        return document.build();
    }

    private static DocumentStatus status(String name) {
        DocumentStatus status = DocumentStatus.valueOf("DOCUMENT_STATUS_" + name);
        if (status == DocumentStatus.DOCUMENT_STATUS_UNSPECIFIED) {
            throw new IllegalStateException(
                    "document status '" + name + "' has no representation in the published "
                            + "language; a consumer would read UNSPECIFIED as 'not retracted'");
        }
        return status;
    }

    private static AuthorityTier authority(String name) {
        return AuthorityTier.valueOf("AUTHORITY_TIER_" + name);
    }
}
