/**
 * Ingestion: turning an external source into something the platform may lawfully use.
 *
 * <h2>Why rights are modelled here and not as a column somewhere</h2>
 *
 * <p>Content licensed for "search and display" is not automatically licensed for embedding into
 * vectors, for extraction into a knowledge graph that outlives the document, or for use as training
 * data. Those are separate permissions that separate contracts grant separately, and a licensor
 * renegotiating one of them does not expect the others to move.
 *
 * <p>So {@code PermittedUse} carries independently revocable flags rather than a single
 * "licensed" boolean. This is the single least reversible decision in the ingestion layer: once a
 * corpus has been embedded and its relationships extracted, discovering that one publisher never
 * permitted embedding means re-deriving everything downstream of that source, and you can only do
 * that if the pipeline recorded which source each derived artefact came from.
 *
 * <p>Rights are attached at acquisition and never inferred afterwards. Inference here means
 * guessing at a contract, and the guess is only discovered to be wrong during an audit.
 *
 * <h2>Anti-corruption</h2>
 *
 * <p>Every external source gets its own translator. A regulator's filing feed, a broker's SFTP
 * drop and a customer's SharePoint tenant have nothing in common beyond eventually producing
 * bytes; letting any of their vocabularies leak past this boundary would put per-vendor conditional
 * logic into the catalog.
 */
package com.atlas.ingestion;
