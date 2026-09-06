package com.vein.scalp.core;

/**
 * Scalping scanner tuning (port of scalp_metrics.ScalpConfig). Defaults match the
 * legacy values exactly so scoring is identical given the same inputs.
 */
public record ScalpConfig(
        int maxWatchMarkets,
        int displayTopN,
        double tradeWindowSec,
        double microvolWindowSec,
        double keepTradeSec,
        double dormantTradeGapSec,
        int depthLevels,
        int detailOrderbookLevels,
        double filterMaxSpreadTicks,
        double filterMinTps,
        double filterMinMicrovolTicks,
        double scoreWeightSpread,
        double scoreWeightTps,
        double scoreWeightMicrovol,
        double scoreWeightDepth,
        double tpsTarget,
        double microvolTargetTicks,
        double microvolCapTicks,
        double wallBidImbalance,
        double wallAskImbalance,
        double wallCancelTpsDropRatio) {

    public static ScalpConfig defaults() {
        return new ScalpConfig(
                50,    // maxWatchMarkets
                25,    // displayTopN
                5.0,   // tradeWindowSec
                5.0,   // microvolWindowSec
                12.0,  // keepTradeSec
                4.0,   // dormantTradeGapSec
                5,     // depthLevels
                10,    // detailOrderbookLevels
                3.0,   // filterMaxSpreadTicks
                1.0,   // filterMinTps
                0.5,   // filterMinMicrovolTicks
                0.30,  // scoreWeightSpread
                0.30,  // scoreWeightTps
                0.20,  // scoreWeightMicrovol
                0.20,  // scoreWeightDepth
                12.0,  // tpsTarget
                6.0,   // microvolTargetTicks
                12.0,  // microvolCapTicks
                1.8,   // wallBidImbalance
                0.55,  // wallAskImbalance
                0.35); // wallCancelTpsDropRatio
    }
}
