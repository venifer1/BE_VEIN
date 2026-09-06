package com.vein.market.provider.upbit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;

import com.vein.common.TimeUtil;
import com.vein.common.Timeframe;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.MarketDataProvider;
import com.vein.market.provider.RawCandle;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Upbit implementation of {@link MarketDataProvider} (표16 / 표23 / 부록 D-3).
 * KRW markets only.
 */
@Component
@Primary
@Slf4j
public class UpbitMarketDataProvider implements MarketDataProvider {

    private static final String CODE = "UPBIT";
    private static final int MAX_COUNT = 200;
    /** Upbit candle_date_time_utc has no offset, e.g. {@code 2026-06-12T15:00:00}. */
    private static final DateTimeFormatter UTC_NO_OFFSET = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** Max markets per /v1/ticker (and /v1/orderbook) call when chunking. */
    private static final int TICKER_CHUNK = 100;
    /** TTL for the cached set of valid KRW markets from /v1/market/all. */
    private static final long VALID_MARKETS_TTL_MS = 600_000L; // 10 min

    private final RestClient restClient;

    /** Cached set of markets Upbit actually lists; refreshed every {@link #VALID_MARKETS_TTL_MS}. */
    private volatile java.util.Set<String> validMarkets = null;
    private volatile long validMarketsAt = 0L;

    public UpbitMarketDataProvider(@Value("${vein.upbit.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<InstrumentInfo> listInstruments() {
        UpbitMarketResponse[] markets;
        try {
            markets = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/market/all")
                            .queryParam("isDetails", false).build())
                    .retrieve()
                    .body(UpbitMarketResponse[].class);
        } catch (RestClientException e) {
            throw new UpbitApiException("Failed to list Upbit markets", e);
        }
        if (markets == null) {
            return List.of();
        }
        List<InstrumentInfo> result = new ArrayList<>();
        for (UpbitMarketResponse m : markets) {
            if (m.market() == null || !m.market().startsWith("KRW-")) {
                continue;
            }
            result.add(new InstrumentInfo(
                    "CRYPTO",
                    "UPBIT",
                    m.market(),
                    m.koreanName(),
                    m.englishName(),
                    "KRW"));
        }
        return result;
    }

    @Override
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<RawCandle> fetchCandles(String providerSymbol, Timeframe tf, int count, Instant to) {
        // 3-day candles are synthesized from daily candles (legacy data/_build_3day.py).
        if (tf == Timeframe.D3) {
            // Need 3x daily candles to build the requested number of 3-day bars.
            List<RawCandle> daily = fetchRaw(providerSymbol, "/v1/candles/days",
                    Math.min(count * 3, MAX_COUNT), to);
            return build3Day(daily);
        }
        return fetchRaw(providerSymbol, endpoint(tf), count, to);
    }

    /** Single REST call returning ascending candles for an Upbit candle endpoint. */
    private List<RawCandle> fetchRaw(String providerSymbol, String path, int count, Instant to) {
        int effectiveCount = Math.min(Math.max(count, 1), MAX_COUNT);
        String toParam = to == null ? null : TimeUtil.toIso(to);

        UpbitCandleResponse[] body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("market", providerSymbol)
                                .queryParam("count", effectiveCount);
                        if (toParam != null) {
                            uriBuilder.queryParam("to", toParam);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(UpbitCandleResponse[].class);
        } catch (RestClientException e) {
            throw new UpbitApiException(
                    "Failed to fetch Upbit candles for " + providerSymbol + " " + path, e);
        }
        if (body == null) {
            return List.of();
        }

        List<RawCandle> candles = new ArrayList<>(body.length);
        for (UpbitCandleResponse c : body) {
            Instant openTime = LocalDateTime.parse(c.candleDateTimeUtc(), UTC_NO_OFFSET)
                    .toInstant(ZoneOffset.UTC);
            candles.add(new RawCandle(
                    openTime,
                    c.openingPrice(),
                    c.highPrice(),
                    c.lowPrice(),
                    c.tradePrice(),
                    c.candleAccTradeVolume(),
                    true));
        }
        // Upbit returns newest -> oldest; reverse to ascending.
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Latest KRW trade prices for the given Upbit markets (e.g. {@code KRW-BTC}).
     * Used by the kimchi premium service.
     */
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public java.util.Map<String, java.math.BigDecimal> fetchTickerPrices(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return java.util.Map.of();
        }
        // Upbit rejects the WHOLE /v1/ticker batch (400) if any market is invalid or
        // delisted (e.g. KRW-MATIC). Drop unknown markets up front, then chunk so a
        // single failing chunk is skipped rather than failing the entire set.
        List<String> valid = filterListed(markets);
        if (valid.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<String, java.math.BigDecimal> out = new java.util.HashMap<>();
        for (List<String> chunk : chunk(valid, TICKER_CHUNK)) {
            UpbitTickerResponse[] body;
            try {
                String joined = String.join(",", chunk);
                body = restClient.get()
                        .uri(b -> b.path("/v1/ticker").queryParam("markets", joined).build())
                        .retrieve()
                        .body(UpbitTickerResponse[].class);
            } catch (RestClientException e) {
                log.warn("Upbit ticker chunk failed ({} markets), skipping: {}",
                        chunk.size(), e.getMessage());
                continue;
            }
            if (body == null) {
                continue;
            }
            for (UpbitTickerResponse t : body) {
                if (t.market() != null && t.tradePrice() != null) {
                    out.put(t.market(), t.tradePrice());
                }
            }
        }
        return out;
    }

    /** Markets Upbit actually lists, cached with a short TTL to avoid refetching. */
    private java.util.Set<String> validKrwMarkets() {
        java.util.Set<String> cached = validMarkets;
        long now = System.currentTimeMillis();
        if (cached != null && (now - validMarketsAt) < VALID_MARKETS_TTL_MS) {
            return cached;
        }
        java.util.Set<String> fresh = new java.util.HashSet<>();
        try {
            for (InstrumentInfo i : listInstruments()) {
                if (i.symbol() != null) {
                    fresh.add(i.symbol());
                }
            }
            validMarkets = fresh;
            validMarketsAt = now;
            return fresh;
        } catch (RuntimeException e) {
            // If the listing call fails, fall back to whatever we had cached (or the
            // raw request) rather than dropping everything.
            log.warn("Could not refresh Upbit valid-market set: {}", e.getMessage());
            return cached;
        }
    }

    /** Keep only markets Upbit lists; if the listing is unavailable, pass through. */
    private List<String> filterListed(List<String> markets) {
        java.util.Set<String> valid = validKrwMarkets();
        if (valid == null || valid.isEmpty()) {
            return markets;
        }
        List<String> out = new ArrayList<>(markets.size());
        for (String m : markets) {
            if (valid.contains(m)) {
                out.add(m);
            } else {
                log.debug("Dropping unlisted Upbit market: {}", m);
            }
        }
        return out;
    }

    private static <T> List<List<T>> chunk(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return out;
    }

    /**
     * Top KRW markets by 24h accumulated trade value (descending). Used to pick the
     * scalp watch list (scalp_metrics max_watch_markets). Returns market codes.
     */
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<String> fetchTopKrwMarketsByValue(int limit) {
        List<InstrumentInfo> all = listInstruments();
        List<String> markets = new ArrayList<>();
        for (InstrumentInfo i : all) {
            markets.add(i.symbol());
        }
        if (markets.isEmpty()) {
            return List.of();
        }
        // Chunk to stay within /v1/ticker limits and so one failing chunk is skipped
        // rather than failing the whole ranking.
        List<UpbitTickerResponse> rows = new ArrayList<>();
        for (List<String> ch : chunk(markets, TICKER_CHUNK)) {
            UpbitTickerResponse[] body;
            try {
                String joined = String.join(",", ch);
                body = restClient.get()
                        .uri(b -> b.path("/v1/ticker").queryParam("markets", joined).build())
                        .retrieve()
                        .body(UpbitTickerResponse[].class);
            } catch (RestClientException e) {
                log.warn("Upbit ranking ticker chunk failed ({} markets), skipping: {}",
                        ch.size(), e.getMessage());
                continue;
            }
            if (body != null) {
                rows.addAll(java.util.Arrays.asList(body));
            }
        }
        rows.removeIf(t -> t.market() == null || t.accTradePrice24h() == null);
        rows.sort((a, b) -> b.accTradePrice24h().compareTo(a.accTradePrice24h()));
        List<String> out = new ArrayList<>();
        for (UpbitTickerResponse t : rows) {
            out.add(t.market());
            if (out.size() >= Math.max(1, limit)) {
                break;
            }
        }
        return out;
    }

    /**
     * Full ticker rows (price + 24h signed change rate + 24h trade value) for every
     * listed KRW market. Used by the movers feature. Chunked so one failing chunk is
     * skipped; unknown markets are dropped via {@link #filterListed}.
     */
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<UpbitTickerResponse> fetchAllKrwTickers() {
        List<String> markets = new ArrayList<>();
        for (InstrumentInfo i : listInstruments()) {
            if (i.symbol() != null) {
                markets.add(i.symbol());
            }
        }
        if (markets.isEmpty()) {
            return List.of();
        }
        List<UpbitTickerResponse> rows = new ArrayList<>();
        for (List<String> ch : chunk(markets, TICKER_CHUNK)) {
            UpbitTickerResponse[] body;
            try {
                String joined = String.join(",", ch);
                body = restClient.get()
                        .uri(b -> b.path("/v1/ticker").queryParam("markets", joined).build())
                        .retrieve()
                        .body(UpbitTickerResponse[].class);
            } catch (RestClientException e) {
                log.warn("Upbit movers ticker chunk failed ({} markets), skipping: {}",
                        ch.size(), e.getMessage());
                continue;
            }
            if (body != null) {
                rows.addAll(java.util.Arrays.asList(body));
            }
        }
        return rows;
    }

    /** Orderbook(s) for the given markets (Upbit accepts a comma-joined list). */
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<UpbitOrderbookResponse> fetchOrderbooks(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return List.of();
        }
        // Same all-or-nothing batch risk as /v1/ticker: drop unlisted markets and
        // chunk so one bad chunk is skipped rather than failing the whole request.
        List<String> valid = filterListed(markets);
        if (valid.isEmpty()) {
            return List.of();
        }
        List<UpbitOrderbookResponse> out = new ArrayList<>();
        for (List<String> chunk : chunk(valid, TICKER_CHUNK)) {
            UpbitOrderbookResponse[] body;
            try {
                String joined = String.join(",", chunk);
                body = restClient.get()
                        .uri(b -> b.path("/v1/orderbook").queryParam("markets", joined).build())
                        .retrieve()
                        .body(UpbitOrderbookResponse[].class);
            } catch (RestClientException e) {
                log.warn("Upbit orderbook chunk failed ({} markets), skipping: {}",
                        chunk.size(), e.getMessage());
                continue;
            }
            if (body != null) {
                out.addAll(java.util.Arrays.asList(body));
            }
        }
        return out;
    }

    /** Recent trade ticks for a single market (newest-first, up to {@code count}). */
    @RateLimiter(name = "upbit")
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    public List<UpbitTradeResponse> fetchRecentTrades(String market, int count) {
        int n = Math.max(1, Math.min(count, 500));
        UpbitTradeResponse[] body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/v1/trades/ticks")
                            .queryParam("market", market)
                            .queryParam("count", n)
                            .build())
                    .retrieve()
                    .body(UpbitTradeResponse[].class);
        } catch (RestClientException e) {
            throw new UpbitApiException("Failed to fetch Upbit trades for " + market, e);
        }
        return body == null ? List.of() : java.util.Arrays.asList(body);
    }

    private static String endpoint(Timeframe tf) {
        return switch (tf) {
            case M15 -> "/v1/candles/minutes/15";
            case H1 -> "/v1/candles/minutes/60";
            case H4 -> "/v1/candles/minutes/240";
            case D1 -> "/v1/candles/days";
            case W1 -> "/v1/candles/weeks";
            case MN1 -> "/v1/candles/months";
            // D3 is synthesized upstream in fetchCandles().
            case D3 -> throw new ApiException(ErrorCode.UNSUPPORTED_TIMEFRAME,
                    "3d is synthesized from daily candles, not a direct Upbit endpoint");
        };
    }

    /**
     * Synthesize ascending 3-day candles from ascending daily candles, grouping
     * 3 days at a time anchored at the most recent day (legacy data/_build_3day.py).
     */
    static List<RawCandle> build3Day(List<RawCandle> dailyAsc) {
        List<RawCandle> out = new ArrayList<>();
        int i = dailyAsc.size();
        while (i >= 3) {
            List<RawCandle> chunk = dailyAsc.subList(i - 3, i);
            java.math.BigDecimal high = chunk.get(0).high();
            java.math.BigDecimal low = chunk.get(0).low();
            java.math.BigDecimal vol = java.math.BigDecimal.ZERO;
            for (RawCandle c : chunk) {
                if (c.high().compareTo(high) > 0) {
                    high = c.high();
                }
                if (c.low().compareTo(low) < 0) {
                    low = c.low();
                }
                vol = vol.add(c.volume() == null ? java.math.BigDecimal.ZERO : c.volume());
            }
            out.add(new RawCandle(
                    chunk.get(0).openTime(),
                    chunk.get(0).open(),
                    high,
                    low,
                    chunk.get(2).close(),
                    vol,
                    true));
            i -= 3;
        }
        Collections.reverse(out);
        return out;
    }
}
