/**
 * Outcome types, exposed as a named interface of the shared kernel.
 *
 * <p>Abstentions and conflicts are first-class results, not error handling. They are modelled here
 * because every context that produces a conclusion must be able to decline to produce one, and
 * because the reporting layer treats "declined" and "answered" as equally valid outcomes to
 * surface.
 */
@org.springframework.modulith.NamedInterface("outcome")
package com.atlas.shared.outcome;
