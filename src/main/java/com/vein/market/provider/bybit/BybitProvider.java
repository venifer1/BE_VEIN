package com.vein.market.provider.bybit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * REAL Bybit public provider (funding_arb_tab.py). Keyless v5 market endpoint
 * {@code /v5/market/tickers?category=linear} returns USDT-perp funding rates and
 * last prices. Funding rate is fractional (e.g. 0.0001) -> percent (×100), as in
 * the legacy tab.
 */
@Component
public class BybitProvider {

    public static final String CODE = "BYBIT";

    private final RestClient restClient;

    public BybitProvider(@Value("${vein.bybit.base-url:https://api.bybit.com}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    public String code() {
        return CODE;
    }

    /**
     * A linear (USDT) perp funding row.
     *
     * @param symbol        e.g. {@code BTCUSDT}
     * @param base          base asset, e.g. {@code BTC}
     * @param fundingPct    funding rate as percent (rate × 100)
     * @param lastPrice     last traded price (USDT)
     * @param nextFundingAt next funding settlement time (UTC), or null
     */
    public record Funding(String symbol, String base, BigDecimal fundingPct,
                          BigDecimal lastPrice, Instant nextFundingAt) {
    }

    @RateLimiter(name = "bybit")
    @Retry(name = "bybit")
    @CircuitBreaker(name = "bybit")
    public List<Funding> fetchLinearFunding() {
        BybitTickersResponse body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/v5/market/tickers").queryParam("category", "linear").build())
                    .retrieve()
                    .body(BybitTickersResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("Bybit tickers failed", e);
        }
        if (body == null) {
            throw new ProviderException("Bybit tickers returned no body");
        }
        if (body.retCode() == null || body.retCode() != 0) {
            throw new ProviderException("Bybit retCode=" + body.retCode() + " retMsg=" + body.retMsg());
        }
        if (body.result() == null || body.result().list() == null) {
            return List.of();
        }
        List<Funding> out = new ArrayList<>();
        for (BybitTickersResponse.Ticker t : body.result().list()) {
            String symbol = t.symbol() == null ? null : t.symbol().toUpperCase();
            if (symbol == null || !symbol.endsWith("USDT")) {
                continue;
            }
            BigDecimal rate = dec(t.fundingRate());
            if (rate == null) {
                continue;
            }
            BigDecimal fundingPct = rate.multiply(BigDecimal.valueOf(100));
            String base = symbol.substring(0, symbol.length() - 4);
            out.add(new Funding(symbol, base, fundingPct, dec(t.lastPrice()), epochMs(t.nextFundingTime())));
        }
        return out;
    }

    private static BigDecimal dec(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant epochMs(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            long ms = Long.parseLong(v.trim());
            return ms <= 0 ? null : Instant.ofEpochMilli(ms);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
