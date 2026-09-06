package com.vein.pattern.top;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.vein.pattern.abc.AbcDetector;
import com.vein.pattern.abc.AbcParams;
import com.vein.pattern.abc.AbcPattern;
import com.vein.pattern.core.Bar;

/**
 * TOP (고점판독) pattern detector — PURE Java port of
 * {@code core/top_detector.py}.
 *
 * <p>Strategy (legacy-faithful): invert the candles (negate O/H/L/C and swap
 * high&harr;low), reuse {@link AbcDetector} to find a "bottom" pattern in the
 * inverted domain, then re-invert the resulting pivot prices back into the real
 * price domain. The inverted-bottom becomes a real top-of-rise: 0 is a local
 * trough (start of the rise), A a peak, B a pullback trough, and the C target
 * (c_100 = B - drop) projects upward above the current price.
 *
 * <p>Top filters applied to each re-inverted candidate (legacy + §4):
 * <ol>
 *   <li>현재가 &ge; B (last close at/above the B pullback level),</li>
 *   <li>C예상가 &gt; 현재가 (projected target above current price — still upside),</li>
 *   <li>B 저점 미이탈 (no final low after B has broken below B's level).</li>
 * </ol>
 *
 * <p>Invalidation rule for produced signals: B_LOW_BREAK (price breaks the B low).
 * Detection is pure: it never references current time or future candles beyond
 * the supplied list.
 */
public class TopDetector {

    public static final String ALGORITHM_VERSION = "top-java-1.0.0";
    public static final String RULE_ID = "TOP";
    public static final String INVALIDATION_RULE = "B_LOW_BREAK";

    private final AbcParams params;

    public TopDetector() {
        this(AbcParams.defaults());
    }

    public TopDetector(AbcParams params) {
        this.params = params;
    }

    /** Negated/high-low-swapped view of a bar (top_detector.invert_candles). */
    private record InvertedBar(Bar src) implements Bar {
        @Override
        public Instant openTime() {
            return src.openTime();
        }

        @Override
        public BigDecimal open() {
            return src.open().negate();
        }

        @Override
        public BigDecimal high() {
            return src.low().negate();
        }

        @Override
        public BigDecimal low() {
            return src.high().negate();
        }

        @Override
        public BigDecimal close() {
            return src.close().negate();
        }

        @Override
        public BigDecimal volume() {
            return src.volume();
        }
    }

    public List<TopPattern> detect(List<? extends Bar> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }
        List<InvertedBar> inverted = new ArrayList<>(candles.size());
        for (Bar b : candles) {
            inverted.add(new InvertedBar(b));
        }

        List<AbcPattern> raw = new AbcDetector(params).detect(inverted);
        if (raw.isEmpty()) {
            return List.of();
        }

        double lastClose = candles.get(candles.size() - 1).closeD();
        double[] lows = new double[candles.size()];
        for (int i = 0; i < lows.length; i++) {
            lows[i] = candles.get(i).lowD();
        }

        List<TopPattern> out = new ArrayList<>();
        for (AbcPattern p : raw) {
            // Re-invert prices back to the real domain (top_detector.invert_pattern_result).
            double p0 = -p.p0Val();
            double pa = -p.pAVal();
            double pb = -p.pBVal();
            double c100 = -p.c100();

            // Filter 1: 현재가 >= B (current price at/above the pullback level).
            if (lastClose < pb) {
                continue;
            }
            // Filter 2: C 예상가 > 현재가 (target still above current price).
            if (c100 <= lastClose) {
                continue;
            }
            // Filter 3: B 저점 미이탈 (no low after B broke below B's level).
            if (breaksBelowAfter(lows, p.idxB(), pb)) {
                continue;
            }

            out.add(new TopPattern(p.idx0(), p.idxA(), p.idxB(), p0, pa, pb, c100, p.score()));
        }
        return out;
    }

    private boolean breaksBelowAfter(double[] lows, int fromIdx, double level) {
        for (int j = fromIdx + 1; j < lows.length; j++) {
            if (lows[j] < level) {
                return true;
            }
        }
        return false;
    }
}
