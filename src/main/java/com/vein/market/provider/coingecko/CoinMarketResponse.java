package com.vein.market.provider.coingecko;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw CoinGecko {@code /coins/markets} row (subset). Stays inside the provider package. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CoinMarketResponse(
        String id,
        String symbol,
        String name,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("market_cap") BigDecimal marketCap,
        @JsonProperty("market_cap_rank") Integer marketCapRank,
        @JsonProperty("fully_diluted_valuation") BigDecimal fullyDilutedValuation,
        @JsonProperty("circulating_supply") BigDecimal circulatingSupply,
        @JsonProperty("total_supply") BigDecimal totalSupply,
        @JsonProperty("max_supply") BigDecimal maxSupply) {
}
