package com.vein.market.provider.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.common.Timeframe;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.MarketDataProvider;
import com.vein.market.provider.ProviderException;
import com.vein.market.provider.RawCandle;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Binance spot REST provider. Used for the USDT leg of the kimchi premium and
 * for crypto charting (symbol like {@code BTCUSDT}). Provider DTOs (raw kline
 * arrays) never leave this package — everything is normalized to {@link RawCandle}.
 */
@Component
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final String CODE = "BINANCE";
    private static final int MAX_LIMIT = 1000;

    private final RestClient restClient;

    public BinanceMarketDataProvider(
            @Value("${vein.binance.base-url:https://api.binance.com}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String code() {
        return CODE;
    }

    /** Binance is a price/candle source only here; instrument discovery is via Upbit/seed. */
    @Override
    public List<InstrumentInfo> listInstruments() {
        return List.of();
    }

    @Override
    @RateLimiter(name = "binance")
    @Retry(name = "binance")
    @CircuitBreaker(name = "binance")
    public List<RawCandle> fetchCandles(String providerSymbol, Timeframe tf, int count, Instant to) {
        int limit = Math.min(Math.max(count, 1), MAX_LIMIT);
        String interval = interval(tf);
        Long endTime = to == null ? null : to.toEpochMilli();
        Object[][] body;
        try {
            body = restClient.get()
                    .uri(b -> {
                        b.path("/api/v3/klines")
                                .queryParam("symbol", providerSymbol.toUpperCase())
                                .queryParam("interval", interval)
                                .queryParam("limit", limit);
                        if (endTime != null) {
                            b.queryParam("endTime", endTime);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(Object[][].class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance klines failed for " + providerSymbol, e);
        }
        if (body == null) {
            return List.of();
        }
        // Binance returns ascending [openTime, open, high, low, close, volume, ...].
        List<RawCandle> out = new ArrayList<>(body.length);
        for (Object[] k : body) {
            if (k.length < 6) {
                continue;
            }
            out.add(new RawCandle(
                    Instant.ofEpochMilli(((Number) k[0]).longValue()),
                    new BigDecimal(String.valueOf(k[1])),
                    new BigDecimal(String.valueOf(k[2])),
                    new BigDecimal(String.valueOf(k[3])),
                    new BigDecimal(String.valueOf(k[4])),
                    new BigDecimal(String.valueOf(k[5])),
                    true));
        }
        return out;
    }

    /** Latest USDT prices for the given symbols (e.g. {@code BTCUSDT}). */
    @RateLimiter(name = "binance")
    @Retry(name = "binance")
    @CircuitBreaker(name = "binance")
    public Map<String, BigDecimal> fetchPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        BinancePrice[] body;
        try {
            // Fetch the full ticker table once; cheaper than N round-trips.
            body = restClient.get()
                    .uri("/api/v3/ticker/price")
                    .retrieve()
                    .body(BinancePrice[].class);
        } catch (RestClientException e) {
            throw new ProviderException("Binance ticker/price failed", e);
        }
        if (body == null) {
            return Map.of();
        }
        java.util.Set<String> want = new java.util.HashSet<>();
        for (String s : symbols) {
            want.add(s.toUpperCase());
        }
        Map<String, BigDecimal> out = new HashMap<>();
        for (BinancePrice p : body) {
            if (p.symbol() != null && want.contains(p.symbol().toUpperCase()) && p.price() != null) {
                out.put(p.symbol().toUpperCase(), new BigDecimal(p.price()));
            }
        }
        return out;
    }

    private static String interval(Timeframe tf) {
        return switch (tf) {
            case M15 -> "15m";
            case H1 -> "1h";
            case H4 -> "4h";
            case D1 -> "1d";
            case D3 -> "3d";
            case W1 -> "1w";
            case MN1 -> "1M";
        };
    }
}
