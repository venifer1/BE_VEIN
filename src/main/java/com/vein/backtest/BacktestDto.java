package com.vein.backtest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vein.signal.SignalType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request/response DTOs for the backtest engine (기획서 §11). Decimals are
 * carried as strings on the way out; times are UTC ISO-8601.
 */
public final class BacktestDto {

    private BacktestDto() {
    }

    /**
     * Backtest run request. Client sends snake_case fields.
     *
     * @param type        pattern family to replay (required)
     * @param market      optional market scope (CRYPTO/US/KOSPI/KOSDAQ)
     * @param timeframe   optional timeframe scope (e.g. {@code 1d})
     * @param targetPct   take-profit threshold in percent (> 0, required)
     * @param stopPct     stop-loss threshold in percent (> 0, required)
     * @param horizon     max hold window: {@code "1d"}/{@code "4h"}/{@code "12h"} or bare hours
     * @param periodDays  lookback in days for the signal universe (default 90)
     * @param feePct      round-trip fee in percent subtracted per trade (default 0.1)
     * @param walkForward when true, split trades chronologically into In-Sample /
     *                    Out-of-Sample and report per-split metrics (default false)
     * @param isRatio     In-Sample fraction of the chronological trade list
     *                    (0.5–0.9, default 0.7; only used when walkForward=true)
     */
    public record RunRequest(
            @NotNull SignalType type,
            String market,
            String timeframe,
            @NotNull java.math.BigDecimal targetPct,
            @NotNull java.math.BigDecimal stopPct,
            @NotNull String horizon,
            Integer periodDays,
            java.math.BigDecimal feePct,
            Boolean walkForward,
            Double isRatio) {
    }

    /** Echoed, normalized parameters. */
    public record Params(
            String type,
            String market,
            String timeframe,
            String targetPct,
            String stopPct,
            String horizon,
            int periodDays,
            String feePct) {
    }

    /** Aggregate metrics over the full trade set. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Metrics(
            int tradeCount,
            String winRate,
            String avgReturnPct,
            String totalReturnPct,
            String profitFactor,
            String maxDrawdownPct,
            String bestPct,
            String worstPct,
            Integer avgHoldBars,
            int skipped) {
    }

    /** One point on the compounded equity curve. */
    public record EquityPoint(String t, String equity) {
    }

    /** A single simulated trade. */
    public record Trade(
            String symbol,
            String name,
            String detectedAt,
            String entry,
            String exit,
            String returnPct,
            String outcome,
            String exitAt) {
    }

    /**
     * Walk-forward (In-Sample / Out-of-Sample) split report. Only present when
     * {@code walk_forward=true} on the request; otherwise the field is null and
     * omitted from the response (global non_null inclusion).
     *
     * @param isRatio        In-Sample fraction applied to the chronological trade list
     * @param splitAt        {@code detected_at} boundary (UTC ISO) of the first OOS trade,
     *                       or null when there are no OOS trades
     * @param inSample       metrics over the first {@code is_ratio} of trades
     * @param outOfSample    metrics over the remaining trades
     * @param overfitWarning true when OOS performance is materially worse than IS
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WalkForward(
            String isRatio,
            String splitAt,
            Metrics inSample,
            Metrics outOfSample,
            boolean overfitWarning) {
    }

    /** Full backtest result envelope payload. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunResponse(
            Params params,
            Metrics metrics,
            List<EquityPoint> equityCurve,
            List<Trade> trades,
            WalkForward walkForward) {
    }
}
