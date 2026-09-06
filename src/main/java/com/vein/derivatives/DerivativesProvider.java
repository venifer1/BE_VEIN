package com.vein.derivatives;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Keyless Binance USD-M Futures provider ({@code https://fapi.binance.com}).
 * Provider DTOs never leave this package — everything is normalized to records.
 * <ul>
 *   <li>{@code /fapi/v1/premiumIndex} — mark price + funding (all symbols, one call)</li>
 *   <li>{@code /fapi/v1/openInterest} — open interest in contracts (per symbol)</li>
 *   <li>{@code /futures/data/globalLongShortAccountRatio} — long/short account ratio</li>
 *   <li>{@code /futures/data/openInterestHist} — OI history</li>
 * </ul>
 * All per-symbol calls are rate-limited; callers wrap each in try/catch so one
 * failure never breaks the batch.
 */
@Component
public class DerivativesProvider {

    public static final String CODE = "BINANCE_FUTURES";

    private final RestClient restClient;

    public DerivativesProvider(
            @Value("${vein.binance.futures-base-url:https://fapi.binance.com}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0 (Derivatives)")
                .build();
    }

    public String code() {
        return CODE;
    }

    /** Mark price + funding for a perp symbol. */
    public record PremiumIndex(String symbol, BigDecimal markPrice,
                               BigDecimal lastFundingRate, Long nextFundingTime) {
    }

    /** A long/short account-ratio point. {@code timestamp} is unix millis. */
    public record LongShortPoint(long timestamp, BigDecimal longShortRatio) {
    }

    /** An open-interest history point. {@code timestamp} is unix millis. */
    public record OiHistPoint(long timestamp, BigDecimal openInterest) {
    }

    /**
     * Mark price + funding for every USD-M perp, keyed by symbol (e.g. {@code BTCUSDT}).
     * One call. Throws {@link ProviderException} on failure.
     */
    @RateLimiter(name = "binance_futures")
    @Retry(name = "binance_futures")
    @CircuitBreaker(name = "binance_futures")
    public Map<String, PremiumIndex> fetchPremiumIndex() {
        PremiumIndexResponse[] body;
        try {
            body = restClient.get()
                    .uri("/fapi/v1/premiumIndex")
                    .retrieve()
                    .body(PremiumIndexResponse[].class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance futures premiumIndex failed", e);
        }
        if (body == null) {
            throw new ProviderException("Binance futures premiumIndex returned no data");
        }
        Map<String, PremiumIndex> out = new HashMap<>();
        for (PremiumIndexResponse p : body) {
            if (p.symbol() == null) {
                continue;
            }
            out.put(p.symbol().toUpperCase(), new PremiumIndex(
                    p.symbol().toUpperCase(), p.markPrice(), p.lastFundingRate(), p.nextFundingTime()));
        }
        return out;
    }

    /** Open interest (in contracts) for a single perp symbol. Throws on failure. */
    @RateLimiter(name = "binance_futures")
    @Retry(name = "binance_futures")
    @CircuitBreaker(name = "binance_futures")
    public BigDecimal fetchOpenInterest(String symbol) {
        OpenInterestResponse body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/fapi/v1/openInterest")
                            .queryParam("symbol", symbol.toUpperCase()).build())
                    .retrieve()
                    .body(OpenInterestResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance futures openInterest failed for " + symbol, e);
        }
        return body == null ? null : body.openInterest();
    }

    /**
     * Long/short account ratio history (ascending by time) for a symbol.
     * {@code period} like {@code 5m}/{@code 1h}; {@code limit} up to 500.
     * Throws on failure.
     */
    @RateLimiter(name = "binance_futures")
    @Retry(name = "binance_futures")
    @CircuitBreaker(name = "binance_futures")
    public List<LongShortPoint> fetchLongShortRatio(String symbol, String period, int limit) {
        LongShortResponse[] body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/futures/data/globalLongShortAccountRatio")
                            .queryParam("symbol", symbol.toUpperCase())
                            .queryParam("period", period)
                            .queryParam("limit", Math.max(1, limit)).build())
                    .retrieve()
                    .body(LongShortResponse[].class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance futures longShortRatio failed for " + symbol, e);
        }
        List<LongShortPoint> out = new ArrayList<>();
        if (body != null) {
            for (LongShortResponse r : body) {
                if (r.timestamp() != null && r.longShortRatio() != null) {
                    out.add(new LongShortPoint(r.timestamp(), r.longShortRatio()));
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
        return out;
    }

    /** Open-interest history (ascending by time) for a symbol. Throws on failure. */
    @RateLimiter(name = "binance_futures")
    @Retry(name = "binance_futures")
    @CircuitBreaker(name = "binance_futures")
    public List<OiHistPoint> fetchOpenInterestHist(String symbol, String period, int limit) {
        OiHistResponse[] body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/futures/data/openInterestHist")
                            .queryParam("symbol", symbol.toUpperCase())
                            .queryParam("period", period)
                            .queryParam("limit", Math.max(1, limit)).build())
                    .retrieve()
                    .body(OiHistResponse[].class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance futures openInterestHist failed for " + symbol, e);
        }
        List<OiHistPoint> out = new ArrayList<>();
        if (body != null) {
            for (OiHistResponse r : body) {
                if (r.timestamp() != null && r.sumOpenInterest() != null) {
                    out.add(new OiHistPoint(r.timestamp(), r.sumOpenInterest()));
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
        return out;
    }

    // ---- provider-internal DTOs (never leave this package) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PremiumIndexResponse(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("markPrice") BigDecimal markPrice,
            @JsonProperty("lastFundingRate") BigDecimal lastFundingRate,
            @JsonProperty("nextFundingTime") Long nextFundingTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenInterestResponse(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("openInterest") BigDecimal openInterest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LongShortResponse(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("longShortRatio") BigDecimal longShortRatio,
            @JsonProperty("timestamp") Long timestamp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OiHistResponse(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("sumOpenInterest") BigDecimal sumOpenInterest,
            @JsonProperty("timestamp") Long timestamp) {
    }
}
