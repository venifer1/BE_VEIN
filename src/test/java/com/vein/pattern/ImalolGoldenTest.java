package com.vein.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.pattern.core.Bar;
import com.vein.pattern.imalol.ImalolDetector;
import com.vein.pattern.imalol.ImalolPattern;

/**
 * Deterministic IMALOL (이말올) unit test on synthetic candle series (no imalol
 * fixture in the repo yet — see README for legacy extraction TODO). Exercises a
 * known IMALOL box (two consecutive bullish candles, 2nd low below 1st low, 2nd
 * volume above 1st, lower-Bollinger touch, no recent rise, not above MA20) and a
 * known non-pattern (flat series + a clearly-bullish-trend series).
 */
class ImalolGoldenTest {

    /**
     * Long steady decline (so MA20 sits above price and the lower Bollinger band
     * hugs recent closes), followed by two bullish candles where the 2nd dips lower
     * (into the band) on higher volume.
     */
    private List<Bar> imalolSeries() {
        List<Bar> bars = new ArrayList<>();
        int idx = 0;
        // 0..27: steady decline from 200 down (no rising closes -> no "recent rise").
        double price = 200;
        for (int i = 0; i < 28; i++) {
            double open = price;
            double close = price - 2.0; // bearish, declining
            double high = open + 0.4;
            double low = close - 1.5;
            bars.add(SyntheticBars.bar(idx++, open, high, low, close, 100));
            price = close;
        }
        // The decline left price ~144. Now the IMALOL pair: two bullish candles.
        double base = price; // ~144
        // 1st candle (prev): bullish, low at base-6.
        double prevOpen = base - 1.0;
        double prevClose = base + 0.5;
        double prevHigh = prevClose + 0.3;
        double prevLow = base - 6.0;
        bars.add(SyntheticBars.bar(idx++, prevOpen, prevHigh, prevLow, prevClose, 100));
        // 2nd candle (cur): bullish, LOWER low than prev, HIGHER volume than prev.
        double curOpen = base - 0.5;
        double curClose = base + 1.5;
        double curHigh = curClose + 0.3;
        double curLow = base - 9.0; // below prevLow -> dips deeper into the band
        bars.add(SyntheticBars.bar(idx++, curOpen, curHigh, curLow, curClose, 250));
        return bars;
    }

    @Test
    void detectsKnownImalolPattern() {
        List<Bar> bars = imalolSeries();
        List<ImalolPattern> patterns = new ImalolDetector().detect(bars);

        assertThat(patterns).as("an IMALOL pattern should be detected").isNotEmpty();
        ImalolPattern p = patterns.get(0);

        assertThat(p.boxes()).as("at least one matched 2-candle box").isNotEmpty();
        ImalolPattern.MatchBox box = p.boxes().get(p.boxes().size() - 1);

        // Rule invariants on the matched box.
        assertThat(box.curLow()).as("2nd low below 1st low").isLessThan(box.prevLow());
        assertThat(box.curIdx()).isEqualTo(box.prevIdx() + 1);
        assertThat(p.anchorIdx()).isEqualTo(box.curIdx());

        // Projected close = anchor cur close; current price = last close.
        assertThat(p.projectedClose()).isEqualTo(box.curClose());
        assertThat(p.currentPrice()).isEqualTo(bars.get(bars.size() - 1).closeD());

        // Bollinger band sampled at the anchor (lower < mid < upper).
        assertThat(p.bollLower()).isLessThan(p.bollMid());
        assertThat(p.bollMid()).isLessThan(p.bollUpper());
    }

    @Test
    void flatSeriesYieldsNoImalol() {
        assertThat(new ImalolDetector().detect(SyntheticBars.flat(60, 100.0)))
                .as("flat series has no IMALOL pattern").isEmpty();
    }

    @Test
    void steadyUptrendYieldsNoImalol() {
        // Rising closes everywhere -> "recent rise" guard and above-MA20 exclude all.
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double open = 100 + i;
            double close = open + 0.8;
            bars.add(SyntheticBars.bar(i, open, close + 0.3, open - 0.3, close, 100));
        }
        assertThat(new ImalolDetector().detect(bars))
                .as("steady uptrend has no IMALOL pattern").isEmpty();
    }
}
