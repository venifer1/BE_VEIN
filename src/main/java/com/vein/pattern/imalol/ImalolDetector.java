package com.vein.pattern.imalol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.vein.indicator.Indicators;
import com.vein.pattern.core.Bar;

/**
 * IMALOL (이말올) detector — PURE Java port of {@code ui/tab_imalol.py}
 * ({@code _build_pattern_row} / {@code _run_one}).
 *
 * <p>For each adjacent candle pair (prev = i-1, cur = i) a box matches when ALL hold:
 * <ol>
 *   <li>no "recent rise": no rising close in [max(1, prev-3), prev) (RECENT_RISE_BARS=3),</li>
 *   <li>연속 양봉 2개: prev.close &gt; prev.open AND cur.close &gt; cur.open,</li>
 *   <li>2nd 저가 &lt; 1st 저가: cur.low &lt; prev.low,</li>
 *   <li>20일선 위 제외: NOT(prev.high &gt; MA20(prev) OR cur.high &gt; MA20(cur)),</li>
 *   <li>2nd 거래량 &gt; 1st: cur.volume &gt; prev.volume,</li>
 *   <li>볼린저 하단(20,2.0) 접촉: prev OR cur has low &le; lower &le; high.</li>
 * </ol>
 *
 * <p>All matched boxes for the series are merged into a single {@link ImalolPattern}
 * (one row per instrument+timeframe in the legacy UI). The anchor is the cur candle
 * of the most recent matched box; the Bollinger band is sampled there. Detection is
 * pure: it never references current time or candles beyond the supplied list.
 */
public class ImalolDetector {

    // 1.1.0: only emit when the matched box just appeared (recency gate),
    // rather than whenever any box exists anywhere in the series.
    public static final String ALGORITHM_VERSION = "imalol-java-1.1.0";
    public static final String RULE_ID = "IMALOL";

    private final ImalolParams params;

    public ImalolDetector() {
        this(ImalolParams.defaults());
    }

    public ImalolDetector(ImalolParams params) {
        this.params = params;
    }

    /**
     * Detect the IMALOL signal for the series, or empty if no box matches.
     * Returns a singleton list (merged) so the call site mirrors the other detectors.
     */
    public List<ImalolPattern> detect(List<? extends Bar> candles) {
        int n = candles.size();
        if (n < 2) {
            return List.of();
        }

        List<BigDecimal> closes = new ArrayList<>(n);
        for (Bar b : candles) {
            closes.add(b.close());
        }
        List<BigDecimal> ma = Indicators.sma(closes, params.maPeriod());
        Indicators.Bollinger boll = Indicators.bollinger(closes, params.bollPeriod(), params.bollK());

        List<ImalolPattern.MatchBox> boxes = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            int prev = i - 1;
            int cur = i;
            if (matches(candles, ma, boll, prev, cur)) {
                Bar p = candles.get(prev);
                Bar c = candles.get(cur);
                boxes.add(new ImalolPattern.MatchBox(
                        prev, cur,
                        p.highD(), p.lowD(),
                        c.highD(), c.lowD(),
                        c.closeD()));
            }
        }

        if (boxes.isEmpty()) {
            return List.of();
        }

        // Anchor = cur of the most recent matched box (legacy keeps the latest as merged_row).
        ImalolPattern.MatchBox anchorBox = boxes.get(boxes.size() - 1);
        int anchorIdx = anchorBox.curIdx();

        // Recency gate: only signal when the box just appeared (its cur candle is
        // within recencyBars of the latest candle). A box that formed long ago is
        // stale — we want freshly-emerged IMALOL, not its mere historical presence.
        if ((n - 1 - anchorIdx) > params.recencyBars()) {
            return List.of();
        }

        double upper = bandAt(boll.upper(), anchorIdx);
        double mid = bandAt(boll.middle(), anchorIdx);
        double lower = bandAt(boll.lower(), anchorIdx);

        double projectedClose = anchorBox.curClose(); // legacy c100 = cur close
        double currentPrice = candles.get(n - 1).closeD();

        // Score: more matched boxes = stronger; clamp 0..100 (alpha completeness placeholder).
        double score = Math.min(100.0, boxes.size() * 25.0);

        return List.of(new ImalolPattern(
                anchorIdx, projectedClose, currentPrice, upper, mid, lower, boxes, score));
    }

    private boolean matches(List<? extends Bar> candles, List<BigDecimal> ma,
                            Indicators.Bollinger boll, int prev, int cur) {
        Bar p = candles.get(prev);
        Bar c = candles.get(cur);

        // 1) no recent rise in the prior bars before prev
        if (hasRecentRise(candles, prev)) {
            return false;
        }
        // 2) two consecutive bullish candles
        if (!(c(p).close > c(p).open && c(c).close > c(c).open)) {
            return false;
        }
        // 3) cur low below prev low
        if (c(c).low >= c(p).low) {
            return false;
        }
        // 4) exclude if either candle's high is above its MA20
        if (isAboveMa(ma, p, prev) || isAboveMa(ma, c, cur)) {
            return false;
        }
        // 5) cur volume strictly above prev volume
        if (c(c).vol <= c(p).vol) {
            return false;
        }
        // 6) Bollinger lower-band touch on prev OR cur
        return touchesLower(boll, p, prev) || touchesLower(boll, c, cur);
    }

    /** Legacy {@code _has_recent_rise}: rising close in [max(1, prev-lookback), prev). */
    private boolean hasRecentRise(List<? extends Bar> candles, int prevIdx) {
        if (prevIdx <= 0) {
            return false;
        }
        int lookback = Math.max(1, params.recentRiseBars());
        int start = Math.max(1, prevIdx - lookback);
        for (int i = start; i < prevIdx; i++) {
            if (candles.get(i).closeD() > candles.get(i - 1).closeD()) {
                return true;
            }
        }
        return false;
    }

    /** Legacy {@code _is_above_ma20}: candle.high &gt; MA20(close) at idx (warmup → false). */
    private boolean isAboveMa(List<BigDecimal> ma, Bar bar, int idx) {
        BigDecimal m = ma.get(idx);
        if (m == null) {
            return false;
        }
        return bar.highD() > m.doubleValue();
    }

    /** Legacy {@code _touches_bollinger_lower}: low &le; lower &le; high (warmup → false). */
    private boolean touchesLower(Indicators.Bollinger boll, Bar bar, int idx) {
        BigDecimal lower = boll.lower().get(idx);
        if (lower == null) {
            return false;
        }
        double lo = bar.lowD();
        double hi = bar.highD();
        double lb = lower.doubleValue();
        return lo <= lb && lb <= hi;
    }

    private double bandAt(List<BigDecimal> band, int idx) {
        BigDecimal v = band.get(idx);
        return v == null ? Double.NaN : v.doubleValue();
    }

    // Small scalar holder to keep the rule body readable.
    private record Scalars(double open, double close, double low, double vol) {
    }

    private Scalars c(Bar b) {
        return new Scalars(b.open().doubleValue(), b.closeD(), b.lowD(), b.volume().doubleValue());
    }
}
