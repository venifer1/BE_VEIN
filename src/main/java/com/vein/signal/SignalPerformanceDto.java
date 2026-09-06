package com.vein.signal;

import java.util.List;

/**
 * Response shapes for signal performance (기획서 §31). Money/qty are
 * BigDecimal-as-String; times are UTC ISO-8601.
 */
public final class SignalPerformanceDto {

    private SignalPerformanceDto() {
    }

    /** {@code GET /api/v1/signals/{id}/performance} body. */
    public record Detail(String signalId, String detectedPrice, String detectedAt,
                         List<HorizonResult> horizons) {
    }

    /** One computed horizon for a signal. */
    public record HorizonResult(String horizon, String price, String returnPct,
                                String mfePct, String maePct, String evaluatedAt) {
    }

    /**
     * One row of {@code GET /api/v1/signals/performance/summary}.
     *
     * <p>{@code bucket} is null for a whole-period aggregate; when the caller asks
     * for {@code bucket=MONTH} it holds the UTC detection month as {@code yyyy-MM}
     * and each (type, market, timeframe) appears once per month. Null fields are
     * omitted from JSON, so existing clients see no change.
     */
    public record SummaryRow(String type, String market, String timeframe, String bucket,
                             String horizon, long sampleSize, String hitRate, String avgReturnPct,
                             String medianReturnPct, String avgMfePct, String avgMaePct) {
    }
}
