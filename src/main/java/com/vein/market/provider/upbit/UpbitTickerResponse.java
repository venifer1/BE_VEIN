package com.vein.market.provider.upbit;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Provider-internal DTO for {@code GET /v1/ticker}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitTickerResponse(
        @JsonProperty("market") String market,
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("signed_change_rate") BigDecimal signedChangeRate,
        @JsonProperty("acc_trade_price_24h") BigDecimal accTradePrice24h) {
}
