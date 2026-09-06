package com.vein.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.pattern.core.Bar;
import com.vein.pattern.top.TopDetector;
import com.vein.pattern.top.TopPattern;

/**
 * Deterministic TOP (고점판독) unit test on synthetic candle series (no top fixture
 * in the repo yet — see README for legacy extraction TODO). Exercises a known
 * TOP pattern (rise to a high, pullback, current price still below the projected
 * C target above) and a known non-pattern (flat series).
 *
 * <p>The TOP detector inverts candles, reuses the ABC detector, then re-inverts:
 * in the real domain 0 is a local low (start of rise), A a local high, B a
 * pullback low, c_100 the projected target above price.
 */
class TopGoldenTest {

    /**
     * Build a series: long gentle decline (warmup) → local low 0 → rise to high A
     * → pullback low B → mild recovery so the last close sits above B but below the
     * C target. Local windows of 5 are respected by spacing the pivots ~12 bars apart.
     */
    private List<Bar> topSeries() {
        List<Bar> bars = new ArrayList<>();
        int idx = 0;
        // 0..11 warmup: roughly flat around 110 (so the 0 dip is a clear prominence).
        for (int i = 0; i < 12; i++) {
            double v = 110 + (i % 2 == 0 ? 0.5 : -0.5); // ~110 flat
            bars.add(SyntheticBars.bar(idx++, v, v + 0.5, v - 0.5, v, 100));
        }
        // 12: the 0 low — a sharp dip to 90 (well below the ~110 prior lows: prominent).
        bars.add(SyntheticBars.bar(idx++, 92, 93, 89.5, 90, 100));
        // 13..26: rise toward A high at 150 (rise = (150-90)/90 = 0.67 >= 0.20).
        for (int i = 0; i < 14; i++) {
            double v = 94 + i * 4.0; // 94 -> 146
            bars.add(SyntheticBars.bar(idx++, v, v + 0.5, v - 0.5, v, 100));
        }
        // 27: the A high (clear local peak at 150).
        bars.add(SyntheticBars.bar(idx++, 149.5, 150.2, 149, 150, 100));
        // 28..40: pullback toward B low at 120 (retrace = (150-120)/(150-90)=0.50, in range).
        for (int i = 0; i < 13; i++) {
            double v = 147.5 - i * 2.3; // 147.5 -> ~120
            bars.add(SyntheticBars.bar(idx++, v, v + 0.5, v - 0.5, v, 100));
        }
        // 41: the B pullback low (clear local trough at 120, above the 0 low of 90).
        bars.add(SyntheticBars.bar(idx++, 120.5, 121, 119.8, 120, 100));
        // 42..54: mild recovery; last close ~130 (>= B 120, < C target 180).
        for (int i = 0; i < 13; i++) {
            double v = 121 + i * 0.75; // 121 -> ~130
            bars.add(SyntheticBars.bar(idx++, v, v + 0.5, v - 0.5, v, 100));
        }
        return bars;
    }

    @Test
    void detectsKnownTopPattern() {
        List<Bar> bars = topSeries();
        List<TopPattern> patterns = new TopDetector().detect(bars);

        assertThat(patterns).as("a TOP pattern should be detected").isNotEmpty();
        TopPattern p = patterns.get(0);

        // Geometry: 0 (low) before A (high) before B (pullback low).
        assertThat(p.idx0()).isLessThan(p.idxA());
        assertThat(p.idxA()).isLessThan(p.idxB());

        // Real-domain price ordering: A is the high, 0 the start low, B the pullback.
        assertThat(p.pAVal()).as("A is the high").isGreaterThan(p.p0Val());
        assertThat(p.pAVal()).isGreaterThan(p.pBVal());
        assertThat(p.pBVal()).as("B above the 0 low").isGreaterThan(p.p0Val());

        // C target projects above the B level and above the last close (still upside).
        double lastClose = bars.get(bars.size() - 1).closeD();
        assertThat(p.c100()).as("C target above current price").isGreaterThan(lastClose);
        assertThat(lastClose).as("current price at/above B").isGreaterThanOrEqualTo(p.pBVal());
    }

    @Test
    void flatSeriesYieldsNoTop() {
        List<TopPattern> patterns = new TopDetector().detect(SyntheticBars.flat(120, 100.0));
        assertThat(patterns).as("flat series has no TOP pattern").isEmpty();
    }
}
