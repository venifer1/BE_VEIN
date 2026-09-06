package com.vein.scalp.core;

import java.util.ArrayList;
import java.util.List;

import com.vein.scalp.core.ScalpInputs.MarketData;
import com.vein.scalp.core.ScalpInputs.OrderbookLevel;
import com.vein.scalp.core.ScalpInputs.Trade;

/**
 * Pure computation of a {@link ScalpSnapshot} from {@link MarketData} — a faithful
 * port of {@code MarketScalpState.snapshot} (spread/TPS/micro-vol/imbalance/wall)
 * plus the {@code build_rankings} eligibility + score. No I/O or mutable state.
 */
public final class ScalpCalculator {

    private final ScalpConfig cfg;

    public ScalpCalculator(ScalpConfig cfg) {
        this.cfg = cfg;
    }

    public ScalpSnapshot compute(MarketData data, double nowSec) {
        List<OrderbookLevel> levels = data.levels() == null ? List.of() : data.levels();
        double bestBid = levels.isEmpty() ? 0.0 : levels.get(0).bidPrice();
        double bestAsk = levels.isEmpty() ? 0.0 : levels.get(0).askPrice();
        double refPrice = bestBid > 0 ? bestBid : (bestAsk > 0 ? bestAsk
                : (data.lastTradePrice() > 0 ? data.lastTradePrice() : 1.0));
        double tick = ScalpMath.upbitKrwTickSize(refPrice);
        double spread = (bestBid > 0 && bestAsk > 0) ? Math.max(0.0, bestAsk - bestBid) : 0.0;
        double spreadTicks = (spread > 0 && tick > 0) ? spread / tick : 0.0;

        List<Trade> trades = data.trades() == null ? List.of() : data.trades();
        double win = cfg.tradeWindowSec();
        int recent = 0;
        int prev = 0;
        int buyCount = 0;
        int sellCount = 0;
        double mvMin = Double.MAX_VALUE;
        double mvMax = -Double.MAX_VALUE;
        boolean hasMv = false;
        double mvWindow = cfg.microvolWindowSec();
        for (Trade t : trades) {
            double ts = t.tsSec();
            if (ts >= nowSec - win) {
                recent++;
                if ("BID".equalsIgnoreCase(t.side())) {
                    buyCount++;
                } else if ("ASK".equalsIgnoreCase(t.side())) {
                    sellCount++;
                }
            } else if (ts >= nowSec - 2 * win && ts < nowSec - win) {
                prev++;
            }
            if (ts >= nowSec - mvWindow) {
                hasMv = true;
                mvMin = Math.min(mvMin, t.price());
                mvMax = Math.max(mvMax, t.price());
            }
        }
        double tps = win > 0 ? recent / win : 0.0;
        double tpsPrev = win > 0 ? prev / win : 0.0;
        double lastTradeGap = data.lastTradeTsSec() > 0 ? (nowSec - data.lastTradeTsSec()) : 9999.0;
        double microvol = hasMv ? (mvMax - mvMin) : 0.0;
        double microvolTicks = (hasMv && tick > 0) ? microvol / tick : 0.0;

        double bidDepth = 0.0;
        double askDepth = 0.0;
        int depthLevels = Math.min(cfg.depthLevels(), levels.size());
        for (int i = 0; i < depthLevels; i++) {
            bidDepth += levels.get(i).bidSize();
            askDepth += levels.get(i).askSize();
        }
        double imbalance = bidDepth / (askDepth + 1e-9);
        double totalDepth = bidDepth + askDepth;
        double depthScore = ScalpMath.depthScore(imbalance, totalDepth);
        String wall = ScalpMath.wallStatus(imbalance, cfg);

        int totalTrades = recent;
        double buyRatio = totalTrades > 0 ? (double) buyCount / totalTrades : 0.0;
        double sellRatio = totalTrades > 0 ? (double) sellCount / totalTrades : 0.0;

        boolean wallCancelWarning = !"neutral".equals(wall)
                && tpsPrev >= cfg.filterMinTps()
                && tps < Math.max(0.5, tpsPrev * cfg.wallCancelTpsDropRatio());

        boolean hasOrderbook = !levels.isEmpty();
        boolean eligible = hasOrderbook
                && spreadTicks > 0
                && spreadTicks <= cfg.filterMaxSpreadTicks()
                && tps >= cfg.filterMinTps()
                && microvolTicks >= cfg.filterMinMicrovolTicks()
                && lastTradeGap < cfg.dormantTradeGapSec();

        double score = ScalpMath.scalpScore(spreadTicks, tps, microvolTicks, depthScore, cfg);

        int topN = Math.max(10, cfg.detailOrderbookLevels());
        List<OrderbookLevel> top = new ArrayList<>(levels.subList(0, Math.min(topN, levels.size())));

        return new ScalpSnapshot(data.market(), data.name(), bestBid, bestAsk, tick, spread, spreadTicks,
                tps, tpsPrev, microvol, microvolTicks, bidDepth, askDepth, imbalance, depthScore, wall,
                wallCancelWarning, lastTradeGap, buyCount, sellCount, buyRatio, sellRatio, totalTrades,
                hasOrderbook, eligible, score, top);
    }
}
