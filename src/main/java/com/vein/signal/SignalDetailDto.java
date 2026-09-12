package com.vein.signal;

import java.util.List;

import com.vein.macro.MacroDto.EventRisk;
import com.vein.signal.SignalDto.InstrumentRef;

/**
 * Full signal detail (API_CONTRACT v2 §4): summary fields plus per-type evidence
 * (ABC/TOP pivots + C_TARGET, TRIANGLE trendlines + subtype, IMALOL bollinger +
 * match boxes), invalidation, chart_range and algorithm_version.
 *
 * <p>{@code eventRisk} (R39): 이벤트 전후 변동성으로 신호 신뢰도가 흔들릴 수 있음을 읽기
 * 시점에 붙이는 라벨. 임박 이벤트가 없으면 null.
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
        String expiresAt,
        String score,
        String currentPrice,
        String cTarget,
        List<EvidenceDto> evidence,
        InvalidationDto invalidation,
        ChartRange chartRange,
        String algorithmVersion,
        EventRisk eventRisk) {

    /** One evidence point (pivot / trend / target / bollinger band / match box). */
    public record EvidenceDto(String type, String candleTime, String price, String payload) {
    }

    /**
     * Invalidation rule and trigger price. For low-break rules (ABC/TOP) the raw {@code price}
     * is the anchor line, but invalidation only fires below {@code effectivePrice}
     * (= price × (1 − bufferPct), R53 완충). Both null for rules without a buffer.
     */
    public record InvalidationDto(String rule, String price, String bufferPct, String effectivePrice) {
    }

    /** Suggested chart overlay window (first/last evidence candle time). */
    public record ChartRange(String from, String to) {
    }
}
