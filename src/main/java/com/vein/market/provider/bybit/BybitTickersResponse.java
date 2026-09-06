package com.vein.market.provider.bybit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw Bybit {@code /v5/market/tickers} response (subset). Stays in the provider package. */
@JsonIgnoreProperties(ignoreUnknown = true)
record BybitTickersResponse(
        @JsonProperty("retCode") Integer retCode,
        @JsonProperty("retMsg") String retMsg,
        Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(List<Ticker> list) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ticker(
            String symbol,
            @JsonProperty("lastPrice") String lastPrice,
            @JsonProperty("fundingRate") String fundingRate,
            @JsonProperty("nextFundingTime") String nextFundingTime) {
    }
}
