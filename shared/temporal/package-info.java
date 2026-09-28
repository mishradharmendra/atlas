/**
 * Time types, exposed as a named interface of the shared kernel.
 *
 * <p>{@code AsOf} and {@code FiscalPeriod} are used by every context that touches evidence, which
 * is most of them. Both are deliberately parameter types rather than ambient state: see
 * {@code ArchitectureRulesTest#domainNeverAsksWhatTimeItIs}.
 */
@org.springframework.modulith.NamedInterface("temporal")
package com.atlas.shared.temporal;
