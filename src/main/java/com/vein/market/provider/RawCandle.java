package com.vein.market.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized OHLCV candle returned by a {@link MarketDataProvider}
 * (NORMALIZATION 표17 / 부록 D-3). {@code openTime} is UTC.
 */
public record RawCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean isFinal) {
}
