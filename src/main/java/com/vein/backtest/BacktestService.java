package com.vein.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.backtest.BacktestDto.EquityPoint;
import com.vein.backtest.BacktestDto.Metrics;
import com.vein.backtest.BacktestDto.Params;
import com.vein.backtest.BacktestDto.RunRequest;
import com.vein.backtest.BacktestDto.RunResponse;
import com.vein.backtest.BacktestDto.Trade;
import com.vein.backtest.BacktestDto.WalkForward;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.pattern.core.Bar;
import com.vein.signal.PatternSignal;
import com.vein.signal.PatternSignalRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Backtest engine (기획서 §11). Replays stored pattern signals as trades,
 * computed on demand from {@code pattern_signals} + {@code candles} (no new
 * table). For each signal it enters at the detection price and scans that
 * instrument's candles (same timeframe, {@code open_time > detected_at}) up to
 * a horizon, exiting at target / stop / horizon, then compounds an equity curve.
 *
 * <p>No future-data leakage: the candle scan is bounded by
 * {@code detected_at + horizon}.
 */
@Service
@Slf4j
public class BacktestService {

    private static final int PCT_SCALE = 4;
    private static final int PRICE_SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_FEE_PCT = new BigDecimal("0.1");
    private static final int DEFAULT_PERIOD_DAYS = 90;
    private static final int MAX_TRADES_RETURNED = 200;
    private static final double DEFAULT_IS_RATIO = 0.7;
    private static final double MIN_IS_RATIO = 0.5;
    private static final double MAX_IS_RATIO = 0.9;
    /** Win-rate drop (percentage points) that flags an overfit warning. */
    private static final BigDecimal WIN_RATE_DROP_PP = new BigDecimal("15");
    /** Cap for profit_factor when there are no losing trades. */
    private static final BigDecimal PROFIT_FACTOR_CAP = new BigDecimal("999.99");

    private static final Pattern HORIZON_PATTERN =
            Pattern.compile("^(\\d+)\\s*([hdwHDW]?)$");

    private final PatternSignalRepository signalRepository;
    private final CandleRepository candleRepository;
    private final InstrumentRepository instrumentRepository;

    public BacktestService(PatternSignalRepository signalRepository,
                           CandleRepository candleRepository,
                           InstrumentRepository instrumentRepository) {
        this.signalRepository = signalRepository;
        this.candleRepository = candleRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public RunResponse run(RunRequest req) {
        // --- Validate ----------------------------------------------------
        List<ApiException.FieldError> errors = new ArrayList<>();
        if (req.type() == null) {
            errors.add(new ApiException.FieldError("type", "REQUIRED"));
        }
        if (req.targetPct() == null || req.targetPct().signum() <= 0) {
            errors.add(new ApiException.FieldError("target_pct", "MUST_BE_POSITIVE"));
        }
        if (req.stopPct() == null || req.stopPct().signum() <= 0) {
            errors.add(new ApiException.FieldError("stop_pct", "MUST_BE_POSITIVE"));
        }
        Duration horizon = null;
        try {
            horizon = parseHorizon(req.horizon());
        } catch (IllegalArgumentException e) {
            errors.add(new ApiException.FieldError("horizon", "INVALID"));
        }
        BigDecimal feePct = req.feePct() == null ? DEFAULT_FEE_PCT : req.feePct();
        if (feePct.signum() < 0) {
            errors.add(new ApiException.FieldError("fee_pct", "MUST_BE_NON_NEGATIVE"));
        }
        int periodDays = req.periodDays() == null ? DEFAULT_PERIOD_DAYS : req.periodDays();
        if (periodDays <= 0) {
            errors.add(new ApiException.FieldError("period_days", "MUST_BE_POSITIVE"));
        }
        boolean walkForward = Boolean.TRUE.equals(req.walkForward());
        double isRatio = req.isRatio() == null ? DEFAULT_IS_RATIO : req.isRatio();
        if (walkForward && (isRatio < MIN_IS_RATIO || isRatio > MAX_IS_RATIO)) {
            errors.add(new ApiException.FieldError("is_ratio", "OUT_OF_RANGE_0.5_0.9"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid backtest parameters", errors);
        }

        String market = (req.market() == null || req.market().isBlank())
                ? null : req.market().toUpperCase();
        String timeframe = (req.timeframe() == null || req.timeframe().isBlank())
                ? null : req.timeframe();

        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(periodDays));

        // --- Universe ----------------------------------------------------
        List<PatternSignal> signals =
                signalRepository.findForBacktest(req.type(), market, timeframe, since);

        // Batch-resolve instrument symbol/name.
        Set<Long> instrumentIds = signals.stream()
                .map(PatternSignal::getInstrumentId)
                .collect(Collectors.toSet());
        Map<Long, Instrument> instruments = instrumentRepository.findAllById(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, i -> i));

        // --- Simulate per signal ----------------------------------------
        List<TradeResult> results = new ArrayList<>(signals.size());
        int skipped = 0;
        for (PatternSignal s : signals) {
            BigDecimal entry = s.getCurrentPrice();
            if (entry == null || entry.signum() <= 0) {
                skipped++;
                continue;
            }
            Instant horizonEnd = s.getDetectedAt().plus(horizon);
            // Candles strictly after detection, bounded by the horizon (no leakage).
            List<Candle> candles = candleRepository.findRangeAsc(
                    s.getInstrumentId(), s.getTimeframe(), s.getDetectedAt(), horizonEnd);
            candles = candles.stream()
                    .filter(c -> c.openTime().isAfter(s.getDetectedAt()))
                    .toList();
            if (candles.isEmpty()) {
                skipped++; // insufficient candle history -> skip, never 500
                continue;
            }
            Sim sim = simulate(entry, candles, req.targetPct(), req.stopPct(), feePct);
            Instrument inst = instruments.get(s.getInstrumentId());
            results.add(new TradeResult(
                    inst == null ? null : inst.getSymbol(),
                    inst == null ? null : inst.getName(),
                    s.getDetectedAt(),
                    entry,
                    sim.exitPrice(),
                    sim.returnPct(),
                    sim.outcome(),
                    sim.exitAt(),
                    sim.holdBars()));
        }

        // --- Metrics + equity curve -------------------------------------
        Metrics metrics = withSkipped(computeMetrics(results), skipped);
        List<EquityPoint> equityCurve = buildEquityCurve(results);

        // --- Optional walk-forward (In-Sample / Out-of-Sample) split ----
        WalkForward walkForwardResult = walkForward
                ? buildWalkForward(results, isRatio)
                : null;

        List<Trade> trades = results.stream()
                .limit(MAX_TRADES_RETURNED)
                .map(this::toTradeDto)
                .toList();

        Params params = new Params(
                req.type().name(),
                market,
                timeframe,
                req.targetPct().stripTrailingZeros().toPlainString(),
                req.stopPct().stripTrailingZeros().toPlainString(),
                req.horizon(),
                periodDays,
                feePct.stripTrailingZeros().toPlainString());

        return new RunResponse(params, metrics, equityCurve, trades, walkForwardResult);
    }

    // --- Walk-forward split ----------------------------------------------

    /**
     * Split {@code results} chronologically by {@code detected_at} at the given
     * In-Sample ratio (first {@code isRatio} = In-Sample, rest = Out-of-Sample)
     * and compute the same {@link Metrics} object for each half.
     *
     * <p>Overfit rule (documented per spec): {@code overfit_warning} is true when
     * the Out-of-Sample set looks materially worse than In-Sample, i.e. either
     * <ul>
     *   <li>OOS {@code avg_return_pct} &lt; 0 while IS {@code avg_return_pct} &gt; 0, OR</li>
     *   <li>OOS {@code win_rate} &lt; IS {@code win_rate} − 15 percentage points.</li>
     * </ul>
     * The split is performed on a copy sorted by {@code detected_at} so trade
     * input order does not affect the boundary. With fewer than 2 trades a split
     * is not meaningful, so In-Sample takes everything and Out-of-Sample is empty.
     */
    private WalkForward buildWalkForward(List<TradeResult> results, double isRatio) {
        List<TradeResult> sorted = new ArrayList<>(results);
        sorted.sort(java.util.Comparator.comparing(TradeResult::detectedAt));

        int n = sorted.size();
        // floor, but keep at least 1 IS trade and (when n>=2) at least 1 OOS trade.
        int isCount = (int) Math.floor(n * isRatio);
        if (n >= 1) {
            isCount = Math.max(1, isCount);
        }
        if (n >= 2) {
            isCount = Math.min(isCount, n - 1);
        }

        List<TradeResult> inSample = sorted.subList(0, isCount);
        List<TradeResult> outOfSample = sorted.subList(isCount, n);

        Metrics isMetrics = computeMetrics(inSample);
        Metrics oosMetrics = computeMetrics(outOfSample);

        String splitAt = outOfSample.isEmpty()
                ? null : TimeUtil.toIso(outOfSample.get(0).detectedAt());

        boolean warning = isOverfit(isMetrics, oosMetrics);

        return new WalkForward(
                BigDecimal.valueOf(isRatio).stripTrailingZeros().toPlainString(),
                splitAt,
                isMetrics,
                oosMetrics,
                warning);
    }

    /** Apply the documented overfit rule to two metrics blocks. */
    private boolean isOverfit(Metrics in, Metrics out) {
        if (in.tradeCount() == 0 || out.tradeCount() == 0) {
            return false; // nothing comparable
        }
        BigDecimal isAvg = new BigDecimal(in.avgReturnPct());
        BigDecimal oosAvg = new BigDecimal(out.avgReturnPct());
        if (oosAvg.signum() < 0 && isAvg.signum() > 0) {
            return true;
        }
        BigDecimal isWin = new BigDecimal(in.winRate());
        BigDecimal oosWin = new BigDecimal(out.winRate());
        return oosWin.compareTo(isWin.subtract(WIN_RATE_DROP_PP)) < 0;
    }

    // --- Pure trade simulation (unit-testable) ---------------------------

    /** Outcome of a single trade. */
    public enum Outcome { WIN, LOSS, TIME }

    /** Pure result of simulating one trade over a candle series. */
    public record Sim(BigDecimal exitPrice, BigDecimal returnPct, Outcome outcome,
                      Instant exitAt, int holdBars) {
    }

    /**
     * Simulate a single trade over an ascending candle series (already filtered
     * to {@code open_time > detected_at} and bounded by the horizon). Entry is
     * at {@code entry}. A candle whose {@code high} reaches the target exits at
     * target (WIN); a candle whose {@code low} reaches the stop exits at stop
     * (LOSS). If both are hit in the same candle, LOSS is taken (conservative).
     * Otherwise the trade exits at the close of the last candle in the window
     * (TIME). The round-trip {@code feePct} is subtracted from the gross return.
     *
     * <p>Pure: depends only on its arguments, so it can be unit-tested on a
     * synthetic series. {@code candles} must be non-empty.
     */
    public static Sim simulate(BigDecimal entry, List<? extends Bar> candles,
                               BigDecimal targetPct, BigDecimal stopPct, BigDecimal feePct) {
        BigDecimal target = entry.add(entry.multiply(targetPct).divide(HUNDRED, 18, RoundingMode.HALF_UP));
        BigDecimal stop = entry.subtract(entry.multiply(stopPct).divide(HUNDRED, 18, RoundingMode.HALF_UP));

        int bars = 0;
        for (Bar c : candles) {
            bars++;
            boolean hitStop = c.low() != null && c.low().compareTo(stop) <= 0;
            boolean hitTarget = c.high() != null && c.high().compareTo(target) >= 0;
            if (hitStop) { // conservative: stop wins ties within a bar
                return finish(entry, stop, Outcome.LOSS, c.openTime(), bars, feePct);
            }
            if (hitTarget) {
                return finish(entry, target, Outcome.WIN, c.openTime(), bars, feePct);
            }
        }
        // No threshold hit within the window -> exit at last close (horizon).
        Bar last = candles.get(candles.size() - 1);
        return finish(entry, last.close(), Outcome.TIME, last.openTime(), bars, feePct);
    }

    private static Sim finish(BigDecimal entry, BigDecimal exit, Outcome outcome,
                              Instant exitAt, int holdBars, BigDecimal feePct) {
        BigDecimal gross = exit.divide(entry, 18, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED);
        BigDecimal net = gross.subtract(feePct).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        return new Sim(exit.setScale(PRICE_SCALE, RoundingMode.HALF_UP), net, outcome, exitAt, holdBars);
    }

    // --- Metrics ---------------------------------------------------------

    /**
     * Compute the full metrics block (win rate / avg / total / profit factor /
     * max drawdown / best / worst / avg hold) over a trade list. Used by the
     * overall result and by each walk-forward split. {@code skipped} is a
     * run-level counter not derivable from a sub-list, so it is set to 0 here;
     * the overall result wraps the value via {@link #withSkipped}.
     */
    private Metrics computeMetrics(List<TradeResult> trades) {
        int n = trades.size();
        if (n == 0) {
            return new Metrics(0, "0", "0", "0", "0", "0", null, null, null, 0);
        }
        int wins = 0;
        long holdSum = 0;
        BigDecimal sumReturn = BigDecimal.ZERO;
        BigDecimal sumPos = BigDecimal.ZERO;
        BigDecimal sumNeg = BigDecimal.ZERO; // negative magnitude (abs)
        BigDecimal best = null;
        BigDecimal worst = null;
        BigDecimal equity = BigDecimal.ONE;
        for (TradeResult t : trades) {
            BigDecimal r = t.returnPct();
            sumReturn = sumReturn.add(r);
            if (t.outcome() == Outcome.WIN) {
                wins++;
            }
            if (r.signum() > 0) {
                sumPos = sumPos.add(r);
            } else if (r.signum() < 0) {
                sumNeg = sumNeg.add(r.abs());
            }
            if (best == null || r.compareTo(best) > 0) {
                best = r;
            }
            if (worst == null || r.compareTo(worst) < 0) {
                worst = r;
            }
            holdSum += t.holdBars();
            equity = equity.multiply(BigDecimal.ONE.add(r.divide(HUNDRED, 18, RoundingMode.HALF_UP)));
        }

        BigDecimal winRate = BigDecimal.valueOf(wins).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
        BigDecimal avgReturn = sumReturn.divide(BigDecimal.valueOf(n), PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalReturn = equity.subtract(BigDecimal.ONE).multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal profitFactor = sumNeg.signum() == 0
                ? (sumPos.signum() == 0 ? BigDecimal.ZERO : PROFIT_FACTOR_CAP)
                : sumPos.divide(sumNeg, 2, RoundingMode.HALF_UP).min(PROFIT_FACTOR_CAP);
        BigDecimal mdd = maxDrawdown(trades);
        int avgHold = (int) Math.round((double) holdSum / n);

        return new Metrics(
                n,
                winRate.toPlainString(),
                avgReturn.toPlainString(),
                totalReturn.toPlainString(),
                profitFactor.toPlainString(),
                mdd.toPlainString(),
                best == null ? null : best.toPlainString(),
                worst == null ? null : worst.toPlainString(),
                avgHold,
                0);
    }

    /** Return a copy of {@code m} with the run-level {@code skipped} count set. */
    private Metrics withSkipped(Metrics m, int skipped) {
        return new Metrics(
                m.tradeCount(), m.winRate(), m.avgReturnPct(), m.totalReturnPct(),
                m.profitFactor(), m.maxDrawdownPct(), m.bestPct(), m.worstPct(),
                m.avgHoldBars(), skipped);
    }

    /** Peak-to-trough max drawdown of the compounded equity curve, in percent (<= 0). */
    private BigDecimal maxDrawdown(List<TradeResult> trades) {
        BigDecimal equity = BigDecimal.ONE;
        BigDecimal peak = BigDecimal.ONE;
        BigDecimal maxDd = BigDecimal.ZERO;
        for (TradeResult t : trades) {
            equity = equity.multiply(
                    BigDecimal.ONE.add(t.returnPct().divide(HUNDRED, 18, RoundingMode.HALF_UP)));
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            if (peak.signum() > 0) {
                BigDecimal dd = equity.subtract(peak).divide(peak, 18, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
                if (dd.compareTo(maxDd) < 0) {
                    maxDd = dd;
                }
            }
        }
        return maxDd.setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private List<EquityPoint> buildEquityCurve(List<TradeResult> trades) {
        List<EquityPoint> curve = new ArrayList<>(trades.size());
        BigDecimal equity = BigDecimal.ONE;
        for (TradeResult t : trades) {
            equity = equity.multiply(
                    BigDecimal.ONE.add(t.returnPct().divide(HUNDRED, 18, RoundingMode.HALF_UP)));
            curve.add(new EquityPoint(
                    TimeUtil.toIso(t.exitAt()),
                    equity.setScale(6, RoundingMode.HALF_UP).toPlainString()));
        }
        return curve;
    }

    private Trade toTradeDto(TradeResult t) {
        return new Trade(
                t.symbol(),
                t.name(),
                TimeUtil.toIso(t.detectedAt()),
                t.entry() == null ? null
                        : t.entry().setScale(PRICE_SCALE, RoundingMode.HALF_UP).toPlainString(),
                t.exitPrice() == null ? null : t.exitPrice().toPlainString(),
                t.returnPct().toPlainString(),
                t.outcome().name(),
                TimeUtil.toIso(t.exitAt()));
    }

    /** Parse {@code "1d"}, {@code "4h"}, {@code "2w"} or bare hours ({@code "12"}). */
    static Duration parseHorizon(String horizon) {
        if (horizon == null || horizon.isBlank()) {
            throw new IllegalArgumentException("horizon required");
        }
        Matcher m = HORIZON_PATTERN.matcher(horizon.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("bad horizon: " + horizon);
        }
        long amount = Long.parseLong(m.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("horizon must be positive");
        }
        String unit = m.group(2).toLowerCase();
        return switch (unit) {
            case "", "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7);
            default -> throw new IllegalArgumentException("bad unit: " + unit);
        };
    }

    /** Internal per-signal result carrying entry context for DTO + metrics. */
    private record TradeResult(String symbol, String name, Instant detectedAt, BigDecimal entry,
                               BigDecimal exitPrice, BigDecimal returnPct, Outcome outcome,
                               Instant exitAt, int holdBars) {
    }
}
