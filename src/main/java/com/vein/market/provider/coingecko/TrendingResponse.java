package com.vein.market.provider.coingecko;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw CoinGecko {@code /search/trending} response (subset). Stays inside the provider package. */
@JsonIgnoreProperties(ignoreUnknown = true)
record TrendingResponse(List<Coin> coins) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Coin(Item item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(
            String id,
            String symbol,
            String name,
            @JsonProperty("market_cap_rank") Integer marketCapRank,
            String thumb,
            Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(@JsonProperty("price_btc") BigDecimal priceBtc) {
    }
}
