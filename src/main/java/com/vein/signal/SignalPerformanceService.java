package com.vein.signal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.signal.SignalPerformanceDto.Detail;
import com.vein.signal.SignalPerformanceDto.HorizonResult;
import com.vein.signal.SignalPerformanceRepository.SummaryRow;

import lombok.extern.slf4j.Slf4j;

/**
 * Signal performance tracking (기획서 §31). For each signal and each elapsed
 * horizon (1h/4h/1d/3d/7d) it computes, from the instrument's own candles:
 * price_at_horizon (close of the candle at/after detected_at+horizon),
 * return_pct, and the max favorable/adverse excursion (mfe/mae) within the
 * window. Rows are upserted idempotently by (signal_id, horizon).
 *
 * <p>This is intentionally OFF the hot scan path — it is driven by a separate
 * scheduled job ({@link SignalPerformanceScheduler}) and the startup runner.
 */
@Service
@Slf4j
public class SignalPerformanceService {

    /** Percent metrics: NUMERIC(12,4). */
    private static final int PCT_SCALE = 4;
    /** Price: NUMERIC(24,8). */
    private static final int PRICE_SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PatternSignalRepository signalRepository;
    private final SignalPerformanceRepository performanceRepository;
    private final CandleRepository candleRepository;

    public SignalPerformanceService(PatternSignalRepository signalRepository,
                                    SignalPerformanceRepository performanceRepository,
                                    CandleRepository candleRepository) {
        this.signalRepository = signalRepository;
        this.performanceRepository = performanceRepository;
        this.candleRepository = candleRepository;
    }

    // --- Scheduled / startup entry point ---------------------------------

    /**
     * Walk every signal and, for each horizon now fully elapsed but not yet
     * stored, compute and upsert its performance row. Per-signal try/catch so one
     * bad signal can't abort the batch. Idempotent by (signal_id, horizon).
     *
     * @return number of (signal, horizon) rows written/updated.
     */
    @Transactional
    public int evaluateDue() {
        Instant now = Instant.now();
        int written = 0;
        for (PatternSignal signal : signalRepository.findAll()) {
            try {
                written += evaluateSignal(signal, now);
            } catch (RuntimeException e) {
                log.warn("signal performance: signal {} failed: {}", signal.getId(), e.getMessage());
            }
        }
        if (written > 0) {
            log.info("signal performance: wrote/updated {} rows", written);
        }
        return written;
    }

    /** Compute every elapsed-but-missing horizon for one signal. */
    int evaluateSignal(PatternSignal signal, Instant now) {
        BigDecimal detectedPrice = signal.getCurrentPrice();
        if (detectedPrice == null || detectedPrice.signum() == 0) {
            return 0; // no detection price -> nothing to measure against
        }
        int written = 0;
        for (PerformanceHorizon h : PerformanceHorizon.values()) {
            Instant horizonEnd = signal.getDetectedAt().plus(h.duration());
            if (horizonEnd.isAfter(now)) {
                continue; // horizon not elapsed yet -> re-evaluate later
            }
            Optional<HorizonMetrics> metrics =
                    computeMetrics(signal, detectedPrice, horizonEnd);
            if (metrics.isEmpty()) {
                continue; // not enough candle history past the horizon yet
            }
            if (upsert(signal.getId(), h.code(), metrics.get())) {
                written++;
            }
        }
        return written;
    }

    /**
     * Compute metrics for a single elapsed horizon from the signal's own
     * timeframe candles in [detected_at, horizonEnd]. Returns empty when the
     * candle history does not yet extend to the horizon (no future-data leakage:
     * the window scan is bounded by horizonEnd).
     */
    private Optional<HorizonMetrics> computeMetrics(PatternSignal signal, BigDecimal detectedPrice,
                                                    Instant horizonEnd) {
        Long instrumentId = signal.getInstrumentId();
        String tf = signal.getTimeframe();
        Instant detectedAt = signal.getDetectedAt();

        // price_at_horizon: close of the first candle at/after horizonEnd.
        List<Candle> atOrAfter = candleRepository.findFromAsc(
                instrumentId, tf, horizonEnd, PageRequest.of(0, 1));
        if (atOrAfter.isEmpty()) {
            return Optional.empty(); // history does not reach the horizon yet
        }
        BigDecimal priceAtHorizon = atOrAfter.get(0).close();

        // Window scan [detected_at, horizonEnd] for MFE/MAE (bounded -> no leakage).
        List<Candle> window = candleRepository.findRangeAsc(
                instrumentId, tf, detectedAt, horizonEnd);
        BigDecimal maxHigh = null;
        BigDecimal minLow = null;
        for (Candle c : window) {
            if (c.high() != null && (maxHigh == null || c.high().compareTo(maxHigh) > 0)) {
                maxHigh = c.high();
            }
            if (c.low() != null && (minLow == null || c.low().compareTo(minLow) < 0)) {
                minLow = c.low();
            }
        }
        // Fold the horizon close into the excursion bounds so MFE/MAE never miss it.
        if (priceAtHorizon != null) {
            if (maxHigh == null || priceAtHorizon.compareTo(maxHigh) > 0) {
                maxHigh = priceAtHorizon;
            }
            if (minLow == null || priceAtHorizon.compareTo(minLow) < 0) {
                minLow = priceAtHorizon;
            }
        }

        BigDecimal price = priceAtHorizon == null ? null
                : priceAtHorizon.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal returnPct = pct(priceAtHorizon, detectedPrice);
        BigDecimal mfePct = pct(maxHigh, detectedPrice);
        BigDecimal maePct = pct(minLow, detectedPrice);
        return Optional.of(new HorizonMetrics(price, returnPct, mfePct, maePct));
    }

    /** (value / base - 1) * 100, scaled to PCT_SCALE. Null value -> null. */
    private BigDecimal pct(BigDecimal value, BigDecimal base) {
        if (value == null || base == null || base.signum() == 0) {
            return null;
        }
        return value.divide(base, 12, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Insert or update the (signal, horizon) row. Returns true if it changed. */
    private boolean upsert(Long signalId, String horizon, HorizonMetrics m) {
        SignalPerformance row = performanceRepository
                .findBySignalIdAndHorizon(signalId, horizon)
                .orElseGet(() -> SignalPerformance.create(signalId, horizon, null, null, null, null));
        row.setPrice(m.price());
        row.setReturnPct(m.returnPct());
        row.setMfePct(m.mfePct());
        row.setMaePct(m.maePct());
        row.setEvaluatedAt(Instant.now());
        performanceRepository.save(row);
        return true;
    }

    private record HorizonMetrics(BigDecimal price, BigDecimal returnPct,
                                  BigDecimal mfePct, BigDecimal maePct) {
    }

    // --- Read side -------------------------------------------------------

    /**
     * Performance detail for one signal. {@code horizons} contains only the
     * computed ones (empty when the signal is too fresh). Never 500s on missing
     * data. Accepts the bare numeric id (matching {@code /signals/{id}}).
     */
    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        PatternSignal signal = signalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SIGNAL_NOT_FOUND,
                        "Signal not found: " + id));

        List<HorizonResult> horizons = performanceRepository
                .findBySignalIdOrderByHorizonAsc(signal.getId()).stream()
                .map(p -> new HorizonResult(
                        p.getHorizon(),
                        str(p.getPrice()),
                        str(p.getReturnPct()),
                        str(p.getMfePct()),
                        str(p.getMaePct()),
                        TimeUtil.toIso(p.getEvaluatedAt())))
                .toList();

        return new Detail(
                "sig_" + signal.getId(),
                str(signal.getCurrentPrice()),
                TimeUtil.toIso(signal.getDetectedAt()),
                horizons);
    }

    /**
     * Aggregate hit-rate / return stats for the given horizon, grouped by
     * (type, market, timeframe). Filters optional. Returns empty list (never
     * 500s) when there is no data. Powers the "이 패턴 historically X% 적중" UI.
     */
    @Transactional(readOnly = true)
    public List<SignalPerformanceDto.SummaryRow> summary(SignalType type, String market,
                                                         String timeframe, String horizon) {
        String marketFilter = (market == null || market.isBlank()) ? null : market.toUpperCase();
        String h = (horizon == null || horizon.isBlank()) ? PerformanceHorizon.D1.code() : horizon;

        List<SummaryRow> rows = performanceRepository.summaryRows(h, type, marketFilter, timeframe);

        // Group in Java so a null-market group is handled cleanly.
        Map<String, List<SummaryRow>> groups = new LinkedHashMap<>();
        for (SummaryRow r : rows) {
            String key = r.getType() + "|" + r.getMarket() + "|" + r.getTimeframe();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<SignalPerformanceDto.SummaryRow> out = new ArrayList<>();
        for (List<SummaryRow> group : groups.values()) {
            SummaryRow head = group.get(0);
            int n = group.size();
            int hits = 0;
            BigDecimal sumReturn = BigDecimal.ZERO;
            BigDecimal sumMfe = BigDecimal.ZERO;
            BigDecimal sumMae = BigDecimal.ZERO;
            int mfeCount = 0;
            int maeCount = 0;
            List<BigDecimal> returns = new ArrayList<>(n);
            for (SummaryRow r : group) {
                BigDecimal ret = r.getReturnPct();
                returns.add(ret);
                sumReturn = sumReturn.add(ret);
                if (ret.signum() > 0) {
                    hits++;
                }
                if (r.getMfePct() != null) {
                    sumMfe = sumMfe.add(r.getMfePct());
                    mfeCount++;
                }
                if (r.getMaePct() != null) {
                    sumMae = sumMae.add(r.getMaePct());
                    maeCount++;
                }
            }
            returns.sort(Comparator.naturalOrder());

            BigDecimal hitRate = BigDecimal.valueOf(hits)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
            BigDecimal avgReturn = scaledAvg(sumReturn, n);
            BigDecimal medianReturn = median(returns);
            BigDecimal avgMfe = mfeCount == 0 ? null : scaledAvg(sumMfe, mfeCount);
            BigDecimal avgMae = maeCount == 0 ? null : scaledAvg(sumMae, maeCount);

            out.add(new SignalPerformanceDto.SummaryRow(
                    head.getType().name(),
                    head.getMarket(),
                    head.getTimeframe(),
                    h,
                    n,
                    str(hitRate),
                    str(avgReturn),
                    str(medianReturn),
                    str(avgMfe),
                    str(avgMae)));
        }
        out.sort(Comparator.comparingLong(SignalPerformanceDto.SummaryRow::sampleSize).reversed());
        return out;
    }

    private BigDecimal scaledAvg(BigDecimal sum, int count) {
        return sum.divide(BigDecimal.valueOf(count), PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Median of a pre-sorted, non-empty list. */
    private BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return null;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal a = sorted.get(n / 2 - 1);
        BigDecimal b = sorted.get(n / 2);
        return a.add(b).divide(BigDecimal.valueOf(2), PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
