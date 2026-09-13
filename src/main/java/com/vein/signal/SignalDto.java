package com.vein.signal;

import java.util.List;

/**
 * Signal summary returned in list responses (API_CONTRACT v2 §4 scanner card).
 * Card fields: type, market, instrument, timeframe, status, score, current_price,
 * c_target (ABC/TOP/TRIANGLE/IMALOL projection), subtype (TRIANGLE), pivot dates 0/A/B.
 */
public record SignalDto(
        String id,
        String type,
        String market,
        String subtype,
        String status,
        InstrumentRef instrument,
        String timeframe,
        String detectedAt,
        String score,
        String patternScore,
        String currentPrice,
        String cTarget,
        PivotDates pivots) {

    /** Minimal instrument reference embedded in a signal payload. */
    public record InstrumentRef(String id, String symbol, String name) {
    }

    /** Pivot anchor dates (0/A/B) for the card summary; null entries when N/A. */
    public record PivotDates(String pivot0, String pivotA, String pivotB) {
    }
}
