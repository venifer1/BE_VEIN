package com.vein.market.candle;

import com.vein.common.TimeUtil;

/**
 * Public candle representation. Times ISO-8601 UTC; numbers decimal-as-string.
 */
public record CandleDto(
        String openTime,
        String open,
        String high,
        String low,
        String close,
        String volume) {

    public static CandleDto from(Candle c) {
        return new CandleDto(
                TimeUtil.toIso(c.openTime()),
                str(c.open()),
                str(c.high()),
                str(c.low()),
                str(c.close()),
                str(c.volume()));
    }

    private static String str(java.math.BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
