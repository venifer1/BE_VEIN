package com.vein.market.provider.alternativeme;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Crypto Fear &amp; Greed index via alternative.me (terminal_tab.py).
 * {@code https://api.alternative.me/fng/?limit=1}.
 */
@Component
public class AlternativeMeProvider {

    public static final String CODE = "ALTERNATIVE_ME";
    /** History is slow-moving (one new point per day); cache ~10 min. */
    private static final long HISTORY_TTL_MS = 600_000L;
    private static final int MAX_DAYS = 365;

    private final RestClient restClient;

    /** Tiny in-memory TTL cache for the history series (keyed by limit). */
    private volatile List<HistoryPoint> historyCache = null;
    private volatile int historyCacheLimit = 0;
    private volatile long historyCacheAt = 0L;

    public AlternativeMeProvider(
            @Value("${vein.alternativeme.base-url:https://api.alternative.me}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public String code() {
        return CODE;
    }

    /** Fear&Greed value (0-100) with its textual classification. */
    public record FearGreed(BigDecimal value, String classification) {
    }

    /** One historical Fear&Greed point. {@code timestamp} is unix seconds (UTC). */
    public record HistoryPoint(long timestamp, BigDecimal value, String classification) {
    }

    @RateLimiter(name = "alternativeme")
    @Retry(name = "alternativeme")
    @CircuitBreaker(name = "alternativeme")
    public FearGreed fetchFearGreed() {
        FngResponse body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/fng/").queryParam("limit", 1).build())
                    .retrieve()
                    .body(FngResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("alternative.me fng failed", e);
        }
        if (body == null || body.data() == null || body.data().isEmpty()) {
            throw new ProviderException("alternative.me fng returned no data");
        }
        FngItem item = body.data().get(0);
        if (item.value() == null) {
            throw new ProviderException("alternative.me fng missing value");
        }
        return new FearGreed(new BigDecimal(item.value()), item.classification());
    }

    /**
     * Fear&amp;Greed history (ascending by timestamp), up to {@code days} points
     * from {@code /fng/?limit=N}. A ~10 min TTL cache spares the public endpoint.
     * Throws {@link ProviderException} on upstream failure (callers degrade to empty).
     */
    @RateLimiter(name = "alternativeme")
    @Retry(name = "alternativeme")
    @CircuitBreaker(name = "alternativeme")
    public List<HistoryPoint> fetchHistory(int days) {
        int limit = Math.min(Math.max(days, 1), MAX_DAYS);
        long now = System.currentTimeMillis();
        List<HistoryPoint> cached = historyCache;
        if (cached != null && historyCacheLimit >= limit && (now - historyCacheAt) < HISTORY_TTL_MS) {
            return cached.size() > limit ? cached.subList(cached.size() - limit, cached.size()) : cached;
        }
        FngResponse body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/fng/").queryParam("limit", limit).build())
                    .retrieve()
                    .body(FngResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("alternative.me fng history failed", e);
        }
        if (body == null || body.data() == null) {
            throw new ProviderException("alternative.me fng history returned no data");
        }
        List<HistoryPoint> out = new ArrayList<>(body.data().size());
        for (FngItem item : body.data()) {
            if (item.value() == null || item.timestamp() == null) {
                continue;
            }
            long ts;
            BigDecimal val;
            try {
                ts = Long.parseLong(item.timestamp().trim());
                val = new BigDecimal(item.value().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new HistoryPoint(ts, val, item.classification()));
        }
        // alternative.me returns newest-first; expose ascending by timestamp.
        out.sort(Comparator.comparingLong(HistoryPoint::timestamp));
        historyCache = out;
        historyCacheLimit = limit;
        historyCacheAt = now;
        return out;
    }

    record FngResponse(List<FngItem> data) {
    }

    record FngItem(String value,
                   @JsonProperty("value_classification") String classification,
                   String timestamp) {
    }
}
