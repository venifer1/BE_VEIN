package com.vein.market.provider.coingecko;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw CoinGecko {@code /global} response (subset). Stays inside the provider package. */
record GlobalResponse(Data data) {

    record Data(@JsonProperty("market_cap_percentage") Map<String, BigDecimal> marketCapPercentage,
                @JsonProperty("total_market_cap") Map<String, BigDecimal> totalMarketCap,
                @JsonProperty("total_volume") Map<String, BigDecimal> totalVolume,
                @JsonProperty("market_cap_change_percentage_24h_usd") BigDecimal marketCapChange24hUsd,
                @JsonProperty("active_cryptocurrencies") Integer activeCryptocurrencies,
                @JsonProperty("updated_at") Long updatedAt) {
    }
}
