package com.vein.scalp;

import java.util.List;

/** Scalp DTOs (GET /scalp/ranking, GET /scalp/{symbol}). */
public final class ScalpDto {

    private ScalpDto() {
    }

    /** A ranking row (API_CONTRACT §4). */
    public record RankingRow(int rank, String symbol, String scalpScore, String spreadTicks,
                             String tps, String microVol, String obImbalance, String wallState) {
    }

    /** Top orderbook level for the detail view. */
    public record OrderbookLevel(String askPrice, String askSize, String bidPrice, String bidSize) {
    }

    /** Detail view: recent buy/sell flow, top orderbook, wall/cancel state. */
    public record Detail(String symbol, String scalpScore, String spreadTicks, String tps,
                         String microVol, String obImbalance, String wallState,
                         boolean wallCancelWarning, String buyRatio, String sellRatio,
                         int recentTradeCount, List<OrderbookLevel> topLevels, String collectedAt) {
    }
}
