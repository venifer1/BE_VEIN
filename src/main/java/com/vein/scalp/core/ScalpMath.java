package com.vein.scalp.core;

/**
 * Pure scalping math (port of scalp_metrics.py free functions + scalp_ranker.py
 * scoring). No I/O, no state — deterministic given inputs, golden-testable.
 */
public final class ScalpMath {

    private ScalpMath() {
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Upbit KRW tick size for a price (scalp_metrics.upbit_krw_tick_size). */
    public static double upbitKrwTickSize(double price) {
        double p = Math.abs(price);
        if (p >= 2_000_000) return 1000.0;
        if (p >= 1_000_000) return 500.0;
        if (p >= 500_000) return 100.0;
        if (p >= 100_000) return 50.0;
        if (p >= 10_000) return 10.0;
        if (p >= 1_000) return 1.0;
        if (p >= 100) return 0.1;
        if (p >= 10) return 0.01;
        if (p >= 1) return 0.001;
        if (p >= 0.1) return 0.0001;
        if (p >= 0.01) return 0.00001;
        if (p >= 0.001) return 0.000001;
        if (p >= 0.0001) return 0.0000001;
        return 0.00000001;
    }

    /** scalp_ranker._spread_score. */
    public static double spreadScore(double spreadTicks) {
        double s = spreadTicks;
        if (s <= 0) return 0.0;
        if (s <= 1.0) return 1.0;
        if (s <= 2.0) return 0.75;
        if (s <= 3.0) return 0.2;
        return 0.0;
    }

    /** scalp_ranker._tps_score. */
    public static double tpsScore(double tps, double target) {
        if (target <= 0) return 0.0;
        return clamp(tps / target, 0.0, 1.0);
    }

    /** scalp_ranker._microvol_score. */
    public static double microvolScore(double microvolTicks, double target, double capTicks) {
        double mv = microvolTicks;
        if (mv <= 0) return 0.0;
        if (mv <= target) return clamp(mv / Math.max(0.5, target), 0.0, 1.0);
        if (mv <= capTicks) return 1.0;
        double excess = mv - capTicks;
        return clamp(1.0 - (excess / Math.max(1.0, capTicks)), 0.1, 1.0);
    }

    /** scalp_metrics.MarketScalpState._depth_score. */
    public static double depthScore(double imbalance, double totalDepth) {
        double strength = clamp(Math.abs(Math.log(Math.max(1e-9, imbalance))), 0.0, 2.0) / 2.0;
        double depthNorm = clamp(Math.log1p(Math.max(0.0, totalDepth)) / 8.0, 0.0, 1.0);
        return clamp((strength * 0.65) + (depthNorm * 0.35), 0.0, 1.0);
    }

    /** scalp_metrics.MarketScalpState._wall_status. */
    public static String wallStatus(double imbalance, ScalpConfig cfg) {
        if (imbalance >= cfg.wallBidImbalance()) {
            return "bid_wall";
        }
        if (imbalance <= cfg.wallAskImbalance()) {
            return "ask_wall";
        }
        return "neutral";
    }

    /**
     * Composite 0..100 scalping score (scalp_ranker.build_rankings). Inputs are the
     * raw per-factor measurements; weights from {@code cfg}.
     */
    public static double scalpScore(double spreadTicks, double tps, double microvolTicks,
                                    double depthScore, ScalpConfig cfg) {
        double spreadS = spreadScore(spreadTicks);
        double tpsS = tpsScore(tps, cfg.tpsTarget());
        double microS = microvolScore(microvolTicks, cfg.microvolTargetTicks(), cfg.microvolCapTicks());
        double depthS = clamp(depthScore, 0.0, 1.0);
        double score01 = (spreadS * cfg.scoreWeightSpread())
                + (tpsS * cfg.scoreWeightTps())
                + (microS * cfg.scoreWeightMicrovol())
                + (depthS * cfg.scoreWeightDepth());
        return Math.round(clamp(score01, 0.0, 1.0) * 100.0 * 10.0) / 10.0;
    }
}
