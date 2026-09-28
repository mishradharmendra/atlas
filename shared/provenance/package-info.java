/**
 * Provenance types, exposed as a named interface of the shared kernel.
 *
 * <p>These travel further than any other types in the system: from the parser that assigns page
 * coordinates, through chunking, embedding, extraction and graph edges, into an agent's reasoning
 * trace, and finally into a sentence in a memo or a value in a spreadsheet cell.
 *
 * <p>That reach is why they live in the kernel rather than in whichever context happened to define
 * them first. Every hop must be reversible for citation click-through and for cascading a
 * retracted document out of everything derived from it.
 */
@org.springframework.modulith.NamedInterface("provenance")
package com.atlas.shared.provenance;
