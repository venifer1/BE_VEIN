package com.vein.strategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.vein.common.TimeUtil;

/**
 * Saved-strategy DTOs (기획서 §12 seed). Client sends snake_case fields; the
 * global Jackson config handles naming. {@code params}/{@code metrics} are
 * carried as raw JSON nodes so they round-trip without double-encoding.
 * {@code created_at} is UTC ISO-8601.
 */
public final class StrategyDto {

    private StrategyDto() {
    }

    /**
     * Create request. {@code type}/{@code market}/{@code timeframe} are read
     * from {@code params}; the whole {@code params} object is the backtest
     * RunRequest and is persisted verbatim.
     */
    public record CreateRequest(String name, JsonNode params, JsonNode metrics) {
    }

    /** Strategy as returned to clients. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id,
            String name,
            String type,
            String market,
            String timeframe,
            JsonNode params,
            JsonNode metrics,
            String createdAt) {

        public static Response from(Strategy s) {
            return new Response(
                    s.getId(),
                    s.getName(),
                    s.getType(),
                    s.getMarket(),
                    s.getTimeframe(),
                    s.getParams(),
                    s.getMetrics(),
                    TimeUtil.toIso(s.getCreatedAt()));
        }
    }

    /**
     * A single performance-tracking snapshot from re-running a strategy's
     * backtest. {@code metrics} is the full backtest metrics block; the
     * denormalized scalars are carried as strings. {@code runAt} is UTC ISO-8601.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunSnapshot(
            Long id,
            Long strategyId,
            JsonNode metrics,
            Integer tradeCount,
            String totalReturnPct,
            String winRate,
            String runAt) {

        public static RunSnapshot from(StrategyRun r) {
            return new RunSnapshot(
                    r.getId(),
                    r.getStrategyId(),
                    r.getMetrics(),
                    r.getTradeCount(),
                    r.getTotalReturnPct() == null ? null : r.getTotalReturnPct().toPlainString(),
                    r.getWinRate() == null ? null : r.getWinRate().toPlainString(),
                    TimeUtil.toIso(r.getRunAt()));
        }
    }
}
