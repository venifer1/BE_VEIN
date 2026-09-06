package com.vein.market.provider.upbit;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Provider-internal DTO for {@code GET /v1/trades/ticks}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitTradeResponse(
        @JsonProperty("market") String market,
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("trade_volume") BigDecimal tradeVolume,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("ask_bid") String askBid) {
}
