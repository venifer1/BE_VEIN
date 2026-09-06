package com.vein.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, side-effect-free technical-indicator math over a closing-price series.
 * Reused by the terminal indicators endpoint (Phase 1) and the pattern/scalp
 * engines (Phase 2/3). All inputs are ascending by time (oldest first).
 *
 * <p>Returned series are aligned to the input length: positions without enough
 * lookback are {@code null}. Money/qty are {@link BigDecimal}; callers stringify.
 */
public final class Indicators {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int SCALE = 8;

    private Indicators() {
    }

    // ---------------------------------------------------------------- SMA

    /** Simple moving average of {@code period}. Index i = avg of closes (i-period+1..i). */
    public static List<BigDecimal> sma(List<BigDecimal> closes, int period) {
        int n = closes.size();
        List<BigDecimal> out = new ArrayList<>(n);
        if (period <= 0) {
            for (int i = 0; i < n; i++) {
                out.add(null);
            }
            return out;
        }
        BigDecimal window = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            window = window.add(closes.get(i));
            if (i >= period) {
                window = window.subtract(closes.get(i - period));
            }
            if (i >= period - 1) {
                out.add(window.divide(BigDecimal.valueOf(period), SCALE, RoundingMode.HALF_UP));
            } else {
                out.add(null);
            }
        }
        return out;
    }

    /** Latest SMA value, or null if fewer than {@code period} closes. */
    public static BigDecimal lastSma(List<BigDecimal> closes, int period) {
        return last(sma(closes, period));
    }

    // ---------------------------------------------------------------- EMA

    /** Exponential moving average, seeded with the SMA of the first {@code period}. */
    public static List<BigDecimal> ema(List<BigDecimal> closes, int period) {
        int n = closes.size();
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(null);
        }
        if (period <= 0 || n < period) {
            return out;
        }
        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal seed = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            seed = seed.add(closes.get(i));
        }
        BigDecimal prev = seed.divide(BigDecimal.valueOf(period), SCALE, RoundingMode.HALF_UP);
        out.set(period - 1, prev);
        for (int i = period; i < n; i++) {
            // ema = close*k + prev*(1-k)
            BigDecimal val = closes.get(i).multiply(k, MC)
                    .add(prev.multiply(BigDecimal.ONE.subtract(k), MC), MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            out.set(i, val);
            prev = val;
        }
        return out;
    }

    // ---------------------------------------------------------------- RSI

    /**
     * Wilder's RSI of {@code period} (default 14). Returns a value in [0,100],
     * aligned to input; null until {@code period} deltas are available.
     */
    public static List<BigDecimal> rsi(List<BigDecimal> closes, int period) {
        int n = closes.size();
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(null);
        }
        if (period <= 0 || n <= period) {
            return out;
        }
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal d = closes.get(i).subtract(closes.get(i - 1));
            if (d.signum() >= 0) {
                gain = gain.add(d);
            } else {
                loss = loss.add(d.abs());
            }
        }
        BigDecimal avgGain = gain.divide(BigDecimal.valueOf(period), MC);
        BigDecimal avgLoss = loss.divide(BigDecimal.valueOf(period), MC);
        out.set(period, rsiFrom(avgGain, avgLoss));
        BigDecimal p = BigDecimal.valueOf(period);
        BigDecimal pm1 = BigDecimal.valueOf(period - 1L);
        for (int i = period + 1; i < n; i++) {
            BigDecimal d = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal g = d.signum() >= 0 ? d : BigDecimal.ZERO;
            BigDecimal l = d.signum() < 0 ? d.abs() : BigDecimal.ZERO;
            avgGain = avgGain.multiply(pm1, MC).add(g, MC).divide(p, MC);
            avgLoss = avgLoss.multiply(pm1, MC).add(l, MC).divide(p, MC);
            out.set(i, rsiFrom(avgGain, avgLoss));
        }
        return out;
    }

    private static BigDecimal rsiFrom(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), MC));
        return rsi.setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- Bollinger

    /**
     * Bollinger Bands: middle = SMA(period), upper/lower = mid ± k*populationStdDev.
     */
    public record Bollinger(List<BigDecimal> upper, List<BigDecimal> middle, List<BigDecimal> lower) {
    }

    public static Bollinger bollinger(List<BigDecimal> closes, int period, double k) {
        int n = closes.size();
        List<BigDecimal> mid = sma(closes, period);
        List<BigDecimal> upper = new ArrayList<>(n);
        List<BigDecimal> lower = new ArrayList<>(n);
        BigDecimal kBd = BigDecimal.valueOf(k);
        for (int i = 0; i < n; i++) {
            BigDecimal m = mid.get(i);
            if (m == null) {
                upper.add(null);
                lower.add(null);
                continue;
            }
            BigDecimal sumSq = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                BigDecimal diff = closes.get(j).subtract(m);
                sumSq = sumSq.add(diff.multiply(diff, MC), MC);
            }
            BigDecimal variance = sumSq.divide(BigDecimal.valueOf(period), MC);
            BigDecimal std = variance.signum() <= 0
                    ? BigDecimal.ZERO
                    : new BigDecimal(Math.sqrt(variance.doubleValue()), MC);
            BigDecimal band = std.multiply(kBd, MC);
            upper.add(m.add(band).setScale(SCALE, RoundingMode.HALF_UP));
            lower.add(m.subtract(band).setScale(SCALE, RoundingMode.HALF_UP));
        }
        return new Bollinger(upper, mid, lower);
    }

    // ---------------------------------------------------------------- MACD

    /** MACD line = EMA(fast)-EMA(slow); signal = EMA(macd, signalPeriod); hist = macd-signal. */
    public record Macd(List<BigDecimal> macd, List<BigDecimal> signal, List<BigDecimal> histogram) {
    }

    public static Macd macd(List<BigDecimal> closes, int fast, int slow, int signalPeriod) {
        int n = closes.size();
        List<BigDecimal> emaFast = ema(closes, fast);
        List<BigDecimal> emaSlow = ema(closes, slow);
        List<BigDecimal> macdLine = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal f = emaFast.get(i);
            BigDecimal s = emaSlow.get(i);
            macdLine.add(f == null || s == null ? null : f.subtract(s));
        }
        // EMA of the macd line over its non-null tail for the signal.
        List<BigDecimal> signal = emaOfNullable(macdLine, signalPeriod);
        List<BigDecimal> hist = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal m = macdLine.get(i);
            BigDecimal sig = signal.get(i);
            hist.add(m == null || sig == null ? null : m.subtract(sig));
        }
        return new Macd(macdLine, signal, hist);
    }

    /** EMA over a list that may have leading nulls; result aligned to input. */
    private static List<BigDecimal> emaOfNullable(List<BigDecimal> series, int period) {
        int n = series.size();
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(null);
        }
        int firstNonNull = -1;
        for (int i = 0; i < n; i++) {
            if (series.get(i) != null) {
                firstNonNull = i;
                break;
            }
        }
        if (firstNonNull < 0 || period <= 0 || n - firstNonNull < period) {
            return out;
        }
        List<BigDecimal> tail = series.subList(firstNonNull, n);
        List<BigDecimal> tailEma = ema(tail, period);
        for (int i = 0; i < tailEma.size(); i++) {
            out.set(firstNonNull + i, tailEma.get(i));
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    public static BigDecimal last(List<BigDecimal> series) {
        for (int i = series.size() - 1; i >= 0; i--) {
            if (series.get(i) != null) {
                return series.get(i);
            }
        }
        return null;
    }
}
