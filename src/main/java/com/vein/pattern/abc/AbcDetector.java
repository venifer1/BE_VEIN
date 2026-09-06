package com.vein.pattern.abc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vein.pattern.core.Bar;
import com.vein.pattern.core.PivotDetector;

/**
 * ABC pattern detector — PURE Java (부록 E-2 / 표 18).
 *
 * <p>Rules (legacy config-faithful):
 * <ol>
 *   <li>0 (peak): prominence vs prior {@code win}-average &ge; (1 + MIN_0_PROMINENCE).</li>
 *   <li>A (trough, after 0): drop_pct = (0 - A)/|0| &ge; MIN_A_DROP_PCT; if STRICT_WAVE,
 *       no high between 0 and A exceeds the 0 value.</li>
 *   <li>B (peak, after A): retrace = (B - A)/(0 - A) in [MIN_B_RETRACE_PCT, MAX_B_RETRACE_PCT];
 *       B &lt; 0 held; no low between A and B breaks below A; no later high after A exceeds B.</li>
 *   <li>C target c_100 = B - (0 - A).</li>
 *   <li>Sort: most-recent B index first; dedup by 0 value; top MAX_PATTERNS.</li>
 * </ol>
 *
 * <p>Invalidation rule for produced signals: A_LOW_BREAK (low breaks A).
 *
 * <p>Detection is restricted to the most recent {@code SEARCH_WINDOW} bars and
 * never references the current time or future candles.
 */
public class AbcDetector {

    public static final String ALGORITHM_VERSION = "abc-java-1.0.1";
    public static final String RULE_ID = "ABC";
    public static final String INVALIDATION_RULE = "A_LOW_BREAK";

    private final AbcParams params;

    public AbcDetector() {
        this(AbcParams.defaults());
    }

    public AbcDetector(AbcParams params) {
        this.params = params;
    }

    public List<AbcPattern> detect(List<? extends Bar> candles) {
        int n = candles.size();
        if (n < 2 * params.localWin() + 1) {
            return List.of();
        }

        // Restrict scan to the most recent SEARCH_WINDOW bars (offset for absolute indexing).
        int offset = Math.max(0, n - params.searchWindow());
        List<? extends Bar> window = candles.subList(offset, n);

        double[] highs = PivotDetector.highs(window);
        double[] lows = PivotDetector.lows(window);

        List<Integer> peaks = PivotDetector.findPeaks(highs, params.localWin());
        List<Integer> troughs = PivotDetector.findTroughs(lows, params.localWin());

        List<AbcPattern> candidates = new ArrayList<>();

        for (int zero : peaks) {
            double zeroVal = highs[zero];
            if (!hasProminence(highs, zero, zeroVal)) {
                continue;
            }
            for (int a : troughs) {
                if (a <= zero) {
                    continue;
                }
                double aVal = lows[a];
                if (aVal >= zeroVal) {
                    continue; // A must be below 0
                }
                double dropPct = (zeroVal - aVal) / Math.abs(zeroVal);
                if (dropPct < params.minADropPct()) {
                    continue;
                }
                if (params.strictWave() && breaksAbove(highs, zero, a, zeroVal)) {
                    continue; // 0 not breached between 0 and A
                }
                for (int b : peaks) {
                    if (b <= a) {
                        continue;
                    }
                    double bVal = highs[b];
                    if (bVal >= zeroVal) {
                        continue; // B must remain below 0
                    }
                    double denom = (zeroVal - aVal);
                    if (denom <= 0) {
                        continue;
                    }
                    double retrace = (bVal - aVal) / denom;
                    if (retrace < params.minBRetracePct() || retrace > params.maxBRetracePct()) {
                        continue;
                    }
                    if (breaksBelow(lows, a, b, aVal)) {
                        continue; // A not departed between A and B
                    }
                    if (!isHighestSinceA(highs, a, bVal)) {
                        continue; // B must be the dominant rebound high after A
                    }
                    double c100 = bVal - (zeroVal - aVal);
                    double score = scoreOf(retrace);
                    candidates.add(new AbcPattern(
                            offset + zero, offset + a, offset + b,
                            zeroVal, aVal, bVal, c100, score));
                }
            }
        }

        // Sort: most recent B first.
        candidates.sort(Comparator.comparingInt(AbcPattern::idxB).reversed());

        // Dedup by 0 value (keep the first, i.e. most-recent-B occurrence).
        Map<Double, AbcPattern> byZero = new LinkedHashMap<>();
        for (AbcPattern p : candidates) {
            byZero.putIfAbsent(p.p0Val(), p);
        }

        return byZero.values().stream()
                .limit(params.maxPatterns())
                .toList();
    }

    /**
     * 0 must stand out from the average of the prior highs by MIN_0_PROMINENCE
     * (legacy {@code abc_detector.detect_abc_patterns}). The prior window is
     * {@code highs[max(0, idx-win) .. idx)} and the average divides by the actual
     * slice length. When the slice is empty (idx == 0) the check is not enforced.
     *
     * <p>Two sign regimes (the TOP scanner inverts candles, so values can be
     * negative):
     * <ul>
     *   <li>positive domain (val &gt; 0, avg &gt; 0): weak if {@code val < avg*(1+p)};</li>
     *   <li>inverted domain (val &lt; 0, avg &lt; 0): a stronger peak is "less
     *       negative", so weak if {@code val < avg*(1-p)};</li>
     *   <li>mixed/zero signs: not enforced (legacy passes through).</li>
     * </ul>
     */
    private boolean hasProminence(double[] highs, int idx, double val) {
        int win = params.localWin();
        int from = Math.max(0, idx - win);
        int len = idx - from;
        if (len <= 0) {
            return true; // no prior window: legacy does not enforce prominence
        }
        double sum = 0;
        for (int j = from; j < idx; j++) {
            sum += highs[j];
        }
        double avg = sum / len;
        double p = params.min0Prominence();
        if (val > 0 && avg > 0) {
            return val >= avg * (1.0 + p);
        }
        if (val < 0 && avg < 0) {
            return val >= avg * (1.0 - p);
        }
        return true; // mixed/zero signs: not filtered (legacy behaviour)
    }

    private boolean breaksAbove(double[] highs, int from, int to, double level) {
        for (int j = from + 1; j < to; j++) {
            if (highs[j] > level) {
                return true;
            }
        }
        return false;
    }

    private boolean breaksBelow(double[] lows, int from, int to, double level) {
        for (int j = from + 1; j < to; j++) {
            if (lows[j] < level) {
                return true;
            }
        }
        return false;
    }

    private boolean isHighestSinceA(double[] highs, int a, double bVal) {
        for (int j = a + 1; j < highs.length; j++) {
            if (highs[j] > bVal) {
                return false;
            }
        }
        return true;
    }

    /**
     * Alpha completeness score (0..100). Higher when the B retrace sits near the
     * 0.618 golden ratio; this is a default completeness value, not the post-launch
     * weighted Pattern Score (표 14).
     */
    private double scoreOf(double retrace) {
        double dist = Math.abs(retrace - 0.618);
        double norm = 1.0 - Math.min(1.0, dist / 0.382);
        return Math.round(norm * 1000.0) / 10.0;
    }
}
