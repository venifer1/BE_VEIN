package com.vein.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class IndicatorsTest {

    private static List<BigDecimal> closes(double... values) {
        List<BigDecimal> out = new ArrayList<>(values.length);
        for (double v : values) {
            out.add(BigDecimal.valueOf(v));
        }
        return out;
    }

    private static double d(BigDecimal v) {
        return v.doubleValue();
    }

    @Test
    void smaComputesTrailingAverage() {
        List<BigDecimal> c = closes(1, 2, 3, 4, 5);
        List<BigDecimal> sma = Indicators.sma(c, 3);
        // first two have insufficient lookback
        assertThat(sma.get(0)).isNull();
        assertThat(sma.get(1)).isNull();
        assertThat(d(sma.get(2))).isEqualTo(2.0);  // (1+2+3)/3
        assertThat(d(sma.get(3))).isEqualTo(3.0);  // (2+3+4)/3
        assertThat(d(sma.get(4))).isEqualTo(4.0);  // (3+4+5)/3
    }

    @Test
    void rsiOnAllGainsIs100() {
        // strictly increasing closes -> no losses -> RSI = 100
        List<BigDecimal> c = closes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        BigDecimal rsi = Indicators.last(Indicators.rsi(c, 14));
        assertThat(rsi).isNotNull();
        assertThat(d(rsi)).isEqualTo(100.0);
    }

    @Test
    void rsiKnownWilderSeries() {
        // Classic Wilder example (StockCharts): 14-period RSI ~ 70.46 after first value.
        double[] prices = {
                44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42,
                45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28
        };
        List<BigDecimal> c = new ArrayList<>();
        for (double p : prices) {
            c.add(BigDecimal.valueOf(p));
        }
        BigDecimal rsi = Indicators.last(Indicators.rsi(c, 14));
        assertThat(rsi).isNotNull();
        // tolerate small rounding differences
        assertThat(d(rsi)).isBetween(70.0, 71.0);
    }

    @Test
    void bollingerCentersOnSmaWithSymmetricBands() {
        List<BigDecimal> c = closes(2, 4, 6, 8, 10, 12, 14, 16, 18, 20,
                22, 24, 26, 28, 30, 32, 34, 36, 38, 40);
        Indicators.Bollinger b = Indicators.bollinger(c, 20, 2.0);
        BigDecimal mid = Indicators.last(b.middle());
        BigDecimal up = Indicators.last(b.upper());
        BigDecimal lo = Indicators.last(b.lower());
        assertThat(d(mid)).isEqualTo(21.0); // mean of 2..40 step 2
        // bands symmetric around mid
        assertThat(d(up) - d(mid)).isCloseTo(d(mid) - d(lo), org.assertj.core.data.Offset.offset(1e-6));
        assertThat(d(up)).isGreaterThan(d(mid));
        assertThat(d(lo)).isLessThan(d(mid));
    }

    @Test
    void macdHistogramIsMacdMinusSignal() {
        List<BigDecimal> c = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            c.add(BigDecimal.valueOf(100 + Math.sin(i / 5.0) * 10));
        }
        Indicators.Macd m = Indicators.macd(c, 12, 26, 9);
        BigDecimal macd = Indicators.last(m.macd());
        BigDecimal sig = Indicators.last(m.signal());
        BigDecimal hist = Indicators.last(m.histogram());
        assertThat(macd).isNotNull();
        assertThat(sig).isNotNull();
        assertThat(d(hist)).isCloseTo(d(macd) - d(sig), org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void insufficientDataYieldsNulls() {
        List<BigDecimal> c = closes(1, 2, 3);
        assertThat(Indicators.lastSma(c, 5)).isNull();
        assertThat(Indicators.last(Indicators.rsi(c, 14))).isNull();
    }
}
