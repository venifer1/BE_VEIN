package com.vein.market.provider.upbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Provider-internal DTO for {@code GET /v1/market/all}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitMarketResponse(
        @JsonProperty("market") String market,
        @JsonProperty("korean_name") String koreanName,
        @JsonProperty("english_name") String englishName) {
}
