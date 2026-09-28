package com.atlas.ingestion.adapter.in.web;

import com.atlas.contracts.v1.PermittedUse;
import com.atlas.contracts.v1.RegisterSourceContractRequest;
import com.atlas.contracts.v1.RegisterSourceContractResponse;
import com.atlas.contracts.v1.RightsMetadata;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Anti-corruption layer between the ingestion module and the published language.
 *
 * <p>The redistribution class is mapped explicitly and refuses {@code UNSPECIFIED}. Defaulting it
 * would mean a caller who omitted the field got whatever the platform assumed, and the assumption
 * that keeps a pipeline running is the permissive one — which is how tenant-private content ends
 * up classified as publishable.
 */
final class IngestionProtoMapper {

    private IngestionProtoMapper() {}

    static String redistribution(com.atlas.contracts.v1.RedistributionClass wire) {
        return switch (wire) {
            case REDISTRIBUTION_CLASS_PUBLIC -> "PUBLIC";
            case REDISTRIBUTION_CLASS_LICENSED_BROAD -> "LICENSED_BROAD";
            case REDISTRIBUTION_CLASS_LICENSED_CLIENTS_ONLY -> "LICENSED_CLIENTS_ONLY";
            case REDISTRIBUTION_CLASS_TENANT_PRIVATE -> "TENANT_PRIVATE";
            case REDISTRIBUTION_CLASS_UNSPECIFIED, UNRECOGNIZED ->
                throw new IllegalArgumentException(
                        "redistribution class is required: a source whose redistribution is "
                                + "unstated cannot be checked before its content is quoted");
        };
    }

    static RegisterSourceContractResponse registered(
            RegisterSourceContractRequest request, Instant at) {
        Set<String> granted = new HashSet<>(request.getPermittedUsesList());

        PermittedUse.Builder uses = PermittedUse.newBuilder()
                .setIndexLexical(granted.contains("INDEX_LEXICAL"))
                .setIndexDense(granted.contains("INDEX_DENSE"))
                .setExtractToGraph(granted.contains("EXTRACT_TO_GRAPH"))
                .setQuoteVerbatim(granted.contains("QUOTE_VERBATIM"))
                .setExportToArtifact(granted.contains("EXPORT_TO_ARTIFACT"))
                .setTrainModels(granted.contains("TRAIN_MODELS"))
                .setMaxQuoteChars(request.getMaxQuoteChars());

        RightsMetadata.Builder rights = RightsMetadata.newBuilder()
                .setSourceId(request.getSourceId())
                .setContractId(request.getContractId())
                .setRedistribution(request.getRedistribution())
                .setRetentionPolicy(request.getRetentionPolicy())
                .setPermittedUse(uses);

        if (!request.getEmbargoUntil().isEmpty()) {
            rights.setEmbargoUntil(Instant.parse(request.getEmbargoUntil()).toString());
        }
        return RegisterSourceContractResponse.newBuilder().setRights(rights).build();
    }
}
