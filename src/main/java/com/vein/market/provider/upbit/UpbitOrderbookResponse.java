package com.vein.market.provider.upbit;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Provider-internal DTO for {@code GET /v1/orderbook}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitOrderbookResponse(
        @JsonProperty("market") String market,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("orderbook_units") List<Unit> orderbookUnits) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Unit(
            @JsonProperty("ask_price") BigDecimal askPrice,
            @JsonProperty("bid_price") BigDecimal bidPrice,
            @JsonProperty("ask_size") BigDecimal askSize,
            @JsonProperty("bid_size") BigDecimal bidSize) {
    }
}
