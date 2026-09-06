package com.vein.scalp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.scalp.collector.ScalpCollector;
import com.vein.scalp.core.ScalpCalculator;
import com.vein.scalp.core.ScalpConfig;
import com.vein.scalp.core.ScalpInputs.MarketData;
import com.vein.scalp.core.ScalpInputs.OrderbookLevel;
import com.vein.scalp.core.ScalpSnapshot;

import lombok.extern.slf4j.Slf4j;

/**
 * Scalping scanner (API_CONTRACT §4 / scalp_ranker.py + scalp_metrics.py). The
 * pure {@link ScalpCalculator} scores microstructure collected by a
 * {@link ScalpCollector} (REST-polling approximation by default); eligible markets
 * are persisted as a ranked batch and served via {@code /scalp/ranking} and
 * {@code /scalp/{symbol}}.
 */
@Service
@Slf4j
public class ScalpService {

    private final ScalpScoreRepository repository;
    private final ScalpCollector collector;
    private final ScalpSidecarClient sidecar;
    private final ScalpConfig config = ScalpConfig.defaults();
    private final ScalpCalculator calculator = new ScalpCalculator(config);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScalpService(ScalpScoreRepository repository, ScalpCollector collector,
                        ScalpSidecarClient sidecar) {
        this.repository = repository;
        this.collector = collector;
        this.sidecar = sidecar;
    }

    /**
     * Current ranking, capped at {@code limit}. PREFERS the real WS-derived sidecar
     * data; falls back to the stored REST-polling approximation when the sidecar is
     * empty/down (already logged inside the client).
     */
    @Transactional(readOnly = true)
    public List<ScalpDto.RankingRow> ranking(int limit) {
        List<ScalpDto.RankingRow> real = sidecar.fetchRanking(limit <= 0 ? config.displayTopN() : limit);
        if (!real.isEmpty()) {
            int cap = limit <= 0 ? config.displayTopN() : Math.min(limit, 100);
            return real.size() > cap ? real.subList(0, cap) : real;
        }
        List<ScalpScore> batch = repository.findLatestBatch();
        int cap = limit <= 0 ? config.displayTopN() : Math.min(limit, 100);
        List<ScalpDto.RankingRow> rows = new ArrayList<>();
        int rank = 1;
        for (ScalpScore s : batch) {
            rows.add(new ScalpDto.RankingRow(
                    rank++, s.getSymbol(), plain(s.getScalpScore()), plain(s.getSpreadTicks()),
                    plain(s.getTps()), plain(s.getMicroVol()), plain(s.getObImbalance()), s.getWallState()));
            if (rows.size() >= cap) {
                break;
            }
        }
        return rows;
    }

    /**
     * Detail for one symbol (recent flow, top orderbook, wall/cancel state). PREFERS
     * the real WS-derived sidecar data; falls back to the stored REST approximation
     * when the sidecar is empty/down (already logged inside the client).
     */
    @Transactional(readOnly = true)
    public ScalpDto.Detail detail(String symbol) {
        String key = symbol == null ? "" : symbol.trim().toUpperCase();
        ScalpDto.Detail real = sidecar.fetchDetail(key);
        if (real != null) {
            return real;
        }
        ScalpScore s = repository.findLatestBySymbol(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No scalp data for " + symbol));
        Map<String, Object> components = parseComponents(s.getComponents());

        List<ScalpDto.OrderbookLevel> levels = new ArrayList<>();
        Object raw = components.get("top_levels");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    levels.add(new ScalpDto.OrderbookLevel(
                            str(m.get("ask_price")), str(m.get("ask_size")),
                            str(m.get("bid_price")), str(m.get("bid_size"))));
                }
            }
        }
        return new ScalpDto.Detail(
                s.getSymbol(), plain(s.getScalpScore()), plain(s.getSpreadTicks()), plain(s.getTps()),
                plain(s.getMicroVol()), plain(s.getObImbalance()), s.getWallState(),
                Boolean.TRUE.equals(components.get("wall_cancel_warning")),
                str(components.get("buy_ratio")), str(components.get("sell_ratio")),
                intOf(components.get("recent_trade_count")), levels,
                TimeUtil.toIso(s.getCollectedAt()));
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc().map(ScalpScore::getCollectedAt).orElse(null);
    }

    /**
     * Collect microstructure, score, and persist the eligible markets as a new
     * ranked batch. Fault-tolerant. Default-off (heavy polling); enabled via flag.
     */
    @Transactional
    public void refresh() {
        double nowSec = System.currentTimeMillis() / 1000.0;
        List<MarketData> markets;
        try {
            markets = collector.collect(config.maxWatchMarkets(), nowSec);
        } catch (RuntimeException e) {
            log.warn("scalp refresh: collection failed: {}", e.getMessage());
            return;
        }
        if (markets.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int saved = 0;
        for (MarketData md : markets) {
            ScalpSnapshot snap = calculator.compute(md, nowSec);
            if (!snap.eligible()) {
                continue;
            }
            repository.save(ScalpScore.of(
                    snap.market(),
                    dec(snap.scalpScore(), 2),
                    dec(snap.spreadTicks(), 4),
                    dec(snap.tps(), 4),
                    dec(snap.microvol(), 4),
                    dec(snap.imbalance(), 6),
                    snap.wall(),
                    componentsJson(snap),
                    now));
            saved++;
        }
        if (saved > 0) {
            repository.deleteByCollectedAtBefore(now);
        }
        log.debug("scalp refresh saved {} eligible markets", saved);
    }

    /** Build the components JSON object (per-factor scores + detail). */
    private String componentsJson(ScalpSnapshot snap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spread", snap.spread());
        m.put("best_bid", snap.bestBid());
        m.put("best_ask", snap.bestAsk());
        m.put("tick_size", snap.tickSize());
        m.put("bid_depth", snap.bidDepth());
        m.put("ask_depth", snap.askDepth());
        m.put("depth_score", snap.depthScore());
        m.put("wall_cancel_warning", snap.wallCancelWarning());
        m.put("last_trade_gap_sec", snap.lastTradeGapSec());
        m.put("buy_count", snap.buyCount());
        m.put("sell_count", snap.sellCount());
        m.put("buy_ratio", round(snap.buyRatio(), 4));
        m.put("sell_ratio", round(snap.sellRatio(), 4));
        m.put("recent_trade_count", snap.recentTradeCount());
        List<Map<String, Object>> levels = new ArrayList<>();
        for (OrderbookLevel l : snap.topLevels()) {
            Map<String, Object> lv = new LinkedHashMap<>();
            lv.put("ask_price", l.askPrice());
            lv.put("ask_size", l.askSize());
            lv.put("bid_price", l.bidPrice());
            lv.put("bid_size", l.bidSize());
            levels.add(lv);
        }
        m.put("top_levels", levels);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseComponents(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static BigDecimal dec(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
