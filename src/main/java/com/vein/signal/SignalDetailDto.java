package com.vein.signal;

import java.util.List;

import com.vein.signal.SignalDto.InstrumentRef;

/**
 * Full signal detail (API_CONTRACT v2 §4): summary fields plus per-type evidence
 * (ABC/TOP pivots + C_TARGET, TRIANGLE trendlines + subtype, IMALOL bollinger +
 * match boxes), invalidation, chart_range and algorithm_version.
 */
public record SignalDetailDto(
        String id,
        String type,
        String market,
        String subtype,
        String status,
        InstrumentRef instrument,
        String timeframe,
        String detectedAt,
        String score,
        String currentPrice,
        String cTarget,
        List<EvidenceDto> evidence,
        InvalidationDto invalidation,
        ChartRange chartRange,
        String algorithmVersion) {

    /** One evidence point (pivot / trend / target / bollinger band / match box). */
    public record EvidenceDto(String type, String candleTime, String price, String payload) {
    }

    /** Invalidation rule and trigger price. */
    public record InvalidationDto(String rule, String price) {
    }

    /** Suggested chart overlay window (first/last evidence candle time). */
    public record ChartRange(String from, String to) {
    }
}
