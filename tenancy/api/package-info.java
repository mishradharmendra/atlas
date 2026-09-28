/**
 * The tenancy context's published language.
 *
 * <p>Metering reads subscriptions through this rather than taking seats and price as parameters.
 * A margin figure whose denominator is supplied by the caller is a figure the caller can make say
 * whatever it likes.
 */
@org.springframework.modulith.NamedInterface("api")
package com.atlas.tenancy.api;
