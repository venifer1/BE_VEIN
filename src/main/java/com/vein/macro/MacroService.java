package com.vein.macro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vein.common.TimeUtil;
import com.vein.macro.FredProvider.Observation;
import com.vein.macro.MacroDto.DollarIndex;
import com.vein.macro.MacroDto.MonetaryAggregate;
import com.vein.macro.MacroDto.Regime;
import com.vein.macro.MacroDto.Signal;
import com.vein.macro.MacroDto.Snapshot;
import com.vein.macro.MacroDto.YieldCurve;
import com.vein.market.index.MarketIndexService;
import com.vein.market.index.MarketIndexService.HistoryPoint;
import com.vein.market.index.MarketIndexService.IndexValue;

import lombok.extern.slf4j.Slf4j;

/**
 * 거시경제/시장국면 엔진 (기획서 §15.1·§39). 국면(BULL/BEAR/RANGE/TRANSITION)을 단일
 * 지표가 아니라 여러 신호의 방향을 합산해 판정한다:
 * <ul>
 *   <li><b>나스닥 추세</b> (내부, keyless) — 최근값 vs ~20p 전 모멘텀</li>
 *   <li><b>공포탐욕</b> (내부, keyless) — 위험선호/회피</li>
 *   <li><b>장단기 금리차 역전</b> (FRED, 선택) — 경기침체 선행</li>
 *   <li><b>달러 방향</b> (FRED, 선택) — 위험자산 역상관</li>
 * </ul>
 * 내부 신호만으로 항상 라벨이 나오고, FRED 신호는 있으면 정확도를 높인다. 상류 실패에
 * 절대 throw하지 않으며 ~10분 TTL 캐시로 값을 재사용한다.
 */
@Service
@Slf4j
public class MacroService {

    private static final long TTL_MS = 600_000L;
    private static final int TREND_LOOKBACK = 20;
    private static final BigDecimal TREND_EPS = new BigDecimal("0.5");   // % 이동 임계
    private static final int FG_GREED = 60;
    private static final int FG_FEAR = 40;

    // FRED series ids
    private static final String DGS3MO = "DGS3MO";
    private static final String DGS2 = "DGS2";
    private static final String DGS10 = "DGS10";
    private static final String M2SL = "M2SL";
    private static final String DXY_BROAD = "DTWEXBGS";

    private final MarketIndexService indexService;
    private final FredProvider fred;

    private volatile Snapshot cache;
    private volatile long cacheAt;

    public MacroService(MarketIndexService indexService, FredProvider fred) {
        this.indexService = indexService;
        this.fred = fred;
    }

    /** Cached macro snapshot; recomputed every {@link #TTL_MS}. Never throws. */
    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        Snapshot cached = cache;
        if (cached != null && (now - cacheAt) < TTL_MS) {
            return cached;
        }
        Snapshot fresh;
        try {
            fresh = build();
        } catch (RuntimeException e) {
            log.warn("macro snapshot build failed: {}", e.getMessage());
            return cached; // keep last-good (or null on first failure)
        }
        cache = fresh;
        cacheAt = now;
        return fresh;
    }

    /** Just the regime label + explanation. */
    public Regime regime() {
        Snapshot s = snapshot();
        return s == null ? null : s.regime();
    }

    private Snapshot build() {
        List<String> sources = new ArrayList<>();
        List<Signal> signals = new ArrayList<>();
        int score = 0;

        // --- 나스닥 추세 (내부, keyless) ---
        BigDecimal trend = trendPct("NASDAQ");
        if (trend != null) {
            sources.add("NASDAQ");
            if (trend.compareTo(TREND_EPS) > 0) {
                score += 1;
                signals.add(new Signal("EQUITY_TREND", "BULLISH",
                        "나스닥 상승추세 (" + signed(trend) + "%, 최근 " + TREND_LOOKBACK + "p)"));
            } else if (trend.compareTo(TREND_EPS.negate()) < 0) {
                score -= 1;
                signals.add(new Signal("EQUITY_TREND", "BEARISH",
                        "나스닥 하락추세 (" + signed(trend) + "%, 최근 " + TREND_LOOKBACK + "p)"));
            } else {
                signals.add(new Signal("EQUITY_TREND", "NEUTRAL",
                        "나스닥 횡보 (" + signed(trend) + "%)"));
            }
        }

        // --- 공포탐욕 (내부, keyless) ---
        Integer fg = fearGreed();
        if (fg != null) {
            sources.add("FEAR_GREED");
            if (fg >= FG_GREED) {
                score += 1;
                signals.add(new Signal("SENTIMENT", "BULLISH", "위험선호 · 공포탐욕 " + fg + "(탐욕)"));
            } else if (fg <= FG_FEAR) {
                score -= 1;
                signals.add(new Signal("SENTIMENT", "BEARISH", "위험회피 · 공포탐욕 " + fg + "(공포)"));
            } else {
                signals.add(new Signal("SENTIMENT", "NEUTRAL", "중립 · 공포탐욕 " + fg));
            }
        }

        // --- 금리차 (FRED, 선택) ---
        YieldCurve yc = yieldCurve();
        if (yc != null) {
            sources.add("FRED:YIELDS");
            if (Boolean.TRUE.equals(yc.inverted())) {
                score -= 1;
                signals.add(new Signal("YIELD_CURVE", "BEARISH",
                        "장단기 금리차 역전 (10Y-2Y " + yc.spread10y2y() + "%p)"));
            } else if (yc.spread10y2y() != null) {
                signals.add(new Signal("YIELD_CURVE", "NEUTRAL",
                        "금리차 정상 (10Y-2Y " + yc.spread10y2y() + "%p)"));
            }
        }

        // --- 달러 방향 (FRED, 선택) ---
        DollarIndex dxy = dollarIndex();
        if (dxy != null) {
            sources.add("FRED:DXY");
            if ("UP".equals(dxy.trend())) {
                score -= 1;
                signals.add(new Signal("DOLLAR", "BEARISH", "달러 강세 (위험자산 역풍)"));
            } else if ("DOWN".equals(dxy.trend())) {
                score += 1;
                signals.add(new Signal("DOLLAR", "BULLISH", "달러 약세 (위험자산 순풍)"));
            } else {
                signals.add(new Signal("DOLLAR", "NEUTRAL", "달러 방향성 약함"));
            }
        }

        Regime regime = label(score, signals);
        return new Snapshot(regime, yc, m2(), dxy, TimeUtil.toIso(java.time.Instant.now()), sources);
    }

    /**
     * BULL/BEAR/RANGE/TRANSITION from the summed score + signal agreement.
     * Package-private for unit testing (R132) — pure, no dependencies.
     */
    static Regime label(int score, List<Signal> signals) {
        long bull = signals.stream().filter(s -> "BULLISH".equals(s.direction())).count();
        long bear = signals.stream().filter(s -> "BEARISH".equals(s.direction())).count();
        String label;
        String summary;
        if (score >= 2) {
            label = "BULL";
            summary = "위험선호 우위 — 상승 국면";
        } else if (score <= -2) {
            label = "BEAR";
            summary = "위험회피 우위 — 하락/방어 국면";
        } else if (bull > 0 && bear > 0) {
            label = "TRANSITION";
            summary = "신호 혼재 — 전환 국면, 방향 확정 전";
        } else {
            label = "RANGE";
            summary = "뚜렷한 방향 없음 — 횡보 국면";
        }
        if (signals.isEmpty()) {
            label = "RANGE";
            summary = "판정에 쓸 데이터가 아직 부족함";
        }
        return new Regime(label, score, summary, signals);
    }

    /** Momentum of an internal index: (latest - value ~lookback points ago) / that, in %. */
    private BigDecimal trendPct(String key) {
        List<HistoryPoint> hist = indexService.history(key, 45);
        if (hist == null || hist.size() < 2) {
            return null;
        }
        // history() is ascending by time.
        BigDecimal latest = parse(hist.get(hist.size() - 1).value());
        int backIdx = Math.max(0, hist.size() - 1 - TREND_LOOKBACK);
        BigDecimal past = parse(hist.get(backIdx).value());
        if (latest == null || past == null || past.signum() == 0) {
            return null;
        }
        return latest.subtract(past)
                .multiply(BigDecimal.valueOf(100))
                .divide(past, 2, RoundingMode.HALF_UP);
    }

    private Integer fearGreed() {
        for (IndexValue v : indexService.latest()) {
            if ("FEAR_GREED".equals(v.key())) {
                BigDecimal val = parse(v.value());
                return val == null ? null : val.intValue();
            }
        }
        return null;
    }

    private YieldCurve yieldCurve() {
        Observation o3m = fred.latestOne(DGS3MO);
        Observation o2y = fred.latestOne(DGS2);
        Observation o10y = fred.latestOne(DGS10);
        if (o2y == null && o10y == null && o3m == null) {
            return null;
        }
        BigDecimal spread102 = (o10y != null && o2y != null) ? o10y.value().subtract(o2y.value()) : null;
        BigDecimal spread103 = (o10y != null && o3m != null) ? o10y.value().subtract(o3m.value()) : null;
        Boolean inverted = spread102 == null ? null : spread102.signum() < 0;
        String asOf = o10y != null ? o10y.date() : (o2y != null ? o2y.date() : o3m.date());
        return new YieldCurve(str(o3m), str(o2y), str(o10y), plain(spread102), plain(spread103), inverted, asOf);
    }

    private MonetaryAggregate m2() {
        List<Observation> obs = fred.latest(M2SL, 13); // newest first; ~13 months for YoY
        if (obs.isEmpty()) {
            return null;
        }
        Observation latest = obs.get(0);
        BigDecimal yoy = null;
        if (obs.size() >= 13) {
            BigDecimal yearAgo = obs.get(12).value();
            if (yearAgo.signum() != 0) {
                yoy = latest.value().subtract(yearAgo)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(yearAgo, 2, RoundingMode.HALF_UP);
            }
        }
        return new MonetaryAggregate(latest.value().toPlainString(), plain(yoy), latest.date());
    }

    private DollarIndex dollarIndex() {
        List<Observation> obs = fred.latest(DXY_BROAD, 25); // newest first
        if (obs.isEmpty()) {
            return null;
        }
        Observation latest = obs.get(0);
        String trend = "FLAT";
        if (obs.size() >= 21) {
            BigDecimal past = obs.get(20).value();
            if (past.signum() != 0) {
                BigDecimal chg = latest.value().subtract(past)
                        .multiply(BigDecimal.valueOf(100)).divide(past, 2, RoundingMode.HALF_UP);
                if (chg.compareTo(TREND_EPS) > 0) {
                    trend = "UP";
                } else if (chg.compareTo(TREND_EPS.negate()) < 0) {
                    trend = "DOWN";
                }
            }
        }
        return new DollarIndex(latest.value().toPlainString(), trend, latest.date());
    }

    static BigDecimal parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Observation o) {
        return o == null ? null : o.value().toPlainString();
    }

    static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    static String signed(BigDecimal v) {
        return v.signum() >= 0 ? "+" + v.toPlainString() : v.toPlainString();
    }
}
