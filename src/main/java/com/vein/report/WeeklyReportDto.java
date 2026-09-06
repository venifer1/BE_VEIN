package com.vein.report;

import java.util.List;

/**
 * Public weekly pattern-performance report (MONETIZATION 단계2 ②).
 *
 * <p>Exposes ONLY aggregated statistics — never individual signals, instruments,
 * or raw candles — so it is safe to serve without authentication and stays on the
 * right side of the "가공 통계는 조언이 아니다" compliance line. All money/percent
 * values are BigDecimal-as-String; {@code generatedAt} is UTC ISO-8601.
 */
public final class WeeklyReportDto {

    private WeeklyReportDto() {
    }

    /** {@code GET /api/v1/public/reports/weekly} body. */
    public record Report(String generatedAt, int windowDays, String horizon,
                         Overall overall, List<PatternStat> rows,
                         List<Highlight> highlights, String disclaimer) {
    }

    /** Whole-window aggregate across every pattern/market/timeframe. */
    public record Overall(long sampleSize, String hitRate, String avgReturnPct) {
    }

    /** One row of the report table: a pattern on one market/timeframe. */
    public record PatternStat(String type, String market, String timeframe,
                              long sampleSize, String hitRate, String avgReturnPct,
                              String medianReturnPct) {
    }

    /** A called-out row (best/worst by hit rate), with a human label. */
    public record Highlight(String kind, String label, String type, String market,
                            String timeframe, long sampleSize, String hitRate,
                            String avgReturnPct) {
    }
}
