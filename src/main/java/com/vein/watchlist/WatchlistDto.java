package com.vein.watchlist;

import java.util.List;

/**
 * Default watchlist representation returned to clients.
 */
public record WatchlistDto(String id, String name, List<Item> items) {

    /**
     * A pinned instrument enriched with its latest price and most-recent signal.
     * {@code lastPrice}/{@code lastPriceAt}/{@code recentSignal} are null when absent.
     */
    public record Item(InstrumentRef instrument,
                       String createdAt,
                       String lastPrice,
                       String lastPriceAt,
                       RecentSignal recentSignal) {
    }

    /** Instrument reference embedded in a watchlist payload. */
    public record InstrumentRef(String id, String symbol, String name) {
    }

    /**
     * Compact subset of the signals-list card for the most recent signal on an
     * instrument (mirrors {@link com.vein.signal.SignalDto} card fields).
     */
    public record RecentSignal(String id,
                               String type,
                               String status,
                               String timeframe,
                               String detectedAt,
                               String subtype) {
    }
}
