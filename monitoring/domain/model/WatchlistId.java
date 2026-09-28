package com.atlas.monitoring.domain.model;

/** Identity of a watchlist. */
public record WatchlistId(String value) {

    public WatchlistId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("watchlist id must not be blank");
        }
    }

    public static WatchlistId of(String value) {
        return new WatchlistId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
