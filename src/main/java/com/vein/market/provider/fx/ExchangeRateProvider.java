package com.vein.market.provider.fx;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * USD/KRW exchange rate (terminal_tab.py): open.er-api.com primary,
 * exchangerate-api.com fallback. Keyless public endpoints.
 */
@Component
public class ExchangeRateProvider {

    public static final String CODE = "EXCHANGE_RATE";

    private final RestClient primary;
    private final RestClient fallback;

    public ExchangeRateProvider(
            @Value("${vein.fx.primary-url:https://open.er-api.com/v6/latest/USD}") String primaryUrl,
            @Value("${vein.fx.fallback-url:https://api.exchangerate-api.com/v4/latest/USD}") String fallbackUrl) {
        this.primary = RestClient.builder().baseUrl(primaryUrl).build();
        this.fallback = RestClient.builder().baseUrl(fallbackUrl).build();
    }

    public String code() {
        return CODE;
    }

    @RateLimiter(name = "exchangerate")
    @Retry(name = "exchangerate")
    @CircuitBreaker(name = "exchangerate")
    public BigDecimal fetchUsdKrw() {
        BigDecimal krw = tryFetch(primary);
        if (krw == null) {
            krw = tryFetch(fallback);
        }
        if (krw == null) {
            throw new ProviderException("USD/KRW unavailable from both providers");
        }
        return krw;
    }

    private BigDecimal tryFetch(RestClient client) {
        try {
            FxResponse body = client.get().retrieve().body(FxResponse.class);
            if (body == null || body.rates() == null) {
                return null;
            }
            return body.rates().get("KRW");
        } catch (RestClientException e) {
            return null;
        }
    }

    record FxResponse(Map<String, BigDecimal> rates) {
    }
}
