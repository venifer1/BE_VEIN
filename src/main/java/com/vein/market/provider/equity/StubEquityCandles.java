package com.vein.market.provider.equity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.vein.common.Timeframe;
import com.vein.market.provider.RawCandle;

/**
 * Deterministic synthetic OHLCV generator for the equity stubs. Output is a
 * pure function of (providerSymbol, timeframe, count, anchor) so the same call
 * always yields the same candles — clearly NOT real market data.
 *
 * <p>TODO: replace with a real yfinance/pykrx bridge (Python sidecar microservice
 * or market-data vendor). Until then equity charts/indicators are illustrative.
 */
final class StubEquityCandles {

    private StubEquityCandles() {
    }

    static List<RawCandle> generate(String providerSymbol, Timeframe tf, int count, Instant to) {
        int n = Math.min(Math.max(count, 1), 1000);
        Instant anchor = (to == null ? Instant.now() : to).truncatedTo(ChronoUnit.DAYS);
        // Equities only use day/3-day/week; map each to a day-multiple step.
        Duration step = tf.duration();

        long seed = providerSymbol == null ? 1L : Math.abs(providerSymbol.hashCode()) + 1L;
        // Base price derived from the symbol so different tickers differ but are stable.
        double base = 50.0 + (seed % 450);

        List<RawCandle> out = new ArrayList<>(n);
        // Oldest first (ascending). Build n bars ending at the anchor.
        for (int i = n - 1; i >= 0; i--) {
            Instant openTime = anchor.minus(step.multipliedBy(i));
            // Smooth pseudo-random walk: deterministic sine + lcg jitter.
            double t = (anchor.getEpochSecond() / 86400.0) - i;
            double drift = Math.sin(t / 7.0) * (base * 0.05);
            double jitter = lcg(seed + i) * (base * 0.02);
            double close = base + drift + jitter;
            double open = base + Math.sin((t - 1) / 7.0) * (base * 0.05);
            double high = Math.max(open, close) + Math.abs(jitter) * 0.5;
            double low = Math.min(open, close) - Math.abs(jitter) * 0.5;
            double volume = 100_000 + Math.abs(lcg(seed * 3 + i)) * 900_000;
            out.add(new RawCandle(
                    openTime,
                    bd(open), bd(high), bd(low), bd(close),
                    bd(volume), true));
        }
        return out;
    }

    /** Deterministic [0,1) value from a 64-bit LCG step. */
    private static double lcg(long x) {
        long v = (2862933555777941757L * x + 3037000493L);
        return ((v >>> 11) & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
