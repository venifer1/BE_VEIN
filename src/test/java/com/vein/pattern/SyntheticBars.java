package com.vein.pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.vein.pattern.core.Bar;

/**
 * Deterministic synthetic candle builder for the TOP/IMALOL unit tests (no fixture
 * files in the repo yet). Times are sequential UTC daily bars; values are exact.
 */
public final class SyntheticBars {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private SyntheticBars() {
    }

    public record TestBar(Instant openTime, BigDecimal open, BigDecimal high,
                          BigDecimal low, BigDecimal close, BigDecimal volume) implements Bar {
    }

    public static TestBar bar(int index, double open, double high, double low, double close, double volume) {
        return new TestBar(
                BASE.plusSeconds(index * 86_400L),
                BigDecimal.valueOf(open),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close),
                BigDecimal.valueOf(volume));
    }

    /** A flat series (all candles identical) — should never trigger any detector. */
    public static List<Bar> flat(int n, double price) {
        List<Bar> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(bar(i, price, price, price, price, 100));
        }
        return out;
    }
}
