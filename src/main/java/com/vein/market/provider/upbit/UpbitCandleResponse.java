package com.vein.market.provider.upbit;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Provider-internal DTO for Upbit candle endpoints (표17 / 부록 D-3).
 * {@code candle_date_time_utc} is a LocalDateTime at UTC (no zone suffix).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitCandleResponse(
        @JsonProperty("candle_date_time_utc") String candleDateTimeUtc,
        @JsonProperty("opening_price") BigDecimal openingPrice,
        @JsonProperty("high_price") BigDecimal highPrice,
        @JsonProperty("low_price") BigDecimal lowPrice,
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("candle_acc_trade_volume") BigDecimal candleAccTradeVolume) {
}
