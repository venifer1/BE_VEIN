package com.vein.tvl;

import java.util.List;

/** TVL DTOs (GET /tvl, GET /tvl/{id}/history). Money as String Decimal. */
public final class TvlDto {

    private TvlDto() {
    }

    /** A TVL list row (rank assigned by current sort). */
    public record Row(Long id, int rank, String entityType, String name, String category,
                      String chains, String tvl, String mcap, String change1d, String change7d) {
    }

    /** A history line point: {@code t} ISO-8601 UTC, {@code tvl} String Decimal. */
    public record HistoryPoint(String t, String tvl) {
    }

    public record History(Long id, String name, List<HistoryPoint> points) {
    }
}
