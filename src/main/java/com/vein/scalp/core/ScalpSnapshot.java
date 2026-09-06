package com.vein.scalp.core;

import java.util.List;

import com.vein.scalp.core.ScalpInputs.OrderbookLevel;

/**
 * Computed scalping snapshot for one market (port of
 * scalp_metrics.MarketScalpState.snapshot + scalp_ranker scoring).
 */
public record ScalpSnapshot(
        String market,
        String name,
        double bestBid,
        double bestAsk,
        double tickSize,
        double spread,
        double spreadTicks,
        double tps,
        double tpsPrev,
        double microvol,
        double microvolTicks,
        double bidDepth,
        double askDepth,
        double imbalance,
        double depthScore,
        String wall,
        boolean wallCancelWarning,
        double lastTradeGapSec,
        int buyCount,
        int sellCount,
        double buyRatio,
        double sellRatio,
        int recentTradeCount,
        boolean hasOrderbook,
        boolean eligible,
        double scalpScore,
        List<OrderbookLevel> topLevels) {
}
