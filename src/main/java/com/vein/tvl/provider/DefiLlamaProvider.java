package com.vein.tvl.provider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * REAL DefiLlama provider (defillama_service.py). Public, keyless API
 * ({@code https://api.llama.fi}): {@code /protocols}, {@code /v2/chains},
 * {@code /protocol/{slug}} (summary + tvl history). Protocols/chains under
 * $1000 TVL are excluded. A small in-process TTL cache (15s list / 30s history)
 * plus last-good fallback keeps the public endpoint from being hammered and
 * survives transient failures, mirroring the legacy {@code _get_json} behavior.
 */
@Component
public class DefiLlamaProvider {

    public static final String CODE = "DEFILLAMA";
    private static final double MIN_TVL_USD = 1_000.0;
    private static final long LIST_TTL_MS = 15_000;
    private static final long HISTORY_TTL_MS = 30_000;

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public DefiLlamaProvider(
            @Value("${vein.defillama.base-url:https://api.llama.fi}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0 (TVL)")
                .build();
    }

    public String code() {
        return CODE;
    }

    /** A protocol TVL row. {@code chains} is the protocol's chain list (may be empty). */
    public record Protocol(String slug, String name, String category, List<String> chains,
                           BigDecimal tvl, BigDecimal mcap, BigDecimal change1d, BigDecimal change7d) {
    }

    /** A chain TVL row (name + total TVL + 7d change). */
    public record Chain(String name, BigDecimal tvl, BigDecimal change7d) {
    }

    /** A single TVL history point ({@code date} = unix seconds). */
    public record HistoryPoint(long date, BigDecimal tvl) {
    }

    @RateLimiter(name = "defillama")
    @Retry(name = "defillama")
    @CircuitBreaker(name = "defillama")
    @SuppressWarnings("unchecked")
    public List<Protocol> fetchProtocols() {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) getJsonList("/protocols", "protocols", LIST_TTL_MS);
        List<Protocol> out = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String name = str(item.get("name"));
            String slug = str(item.get("slug"));
            BigDecimal tvl = dec(item.get("tvl"));
            if (name == null || slug == null || tvl == null || tvl.doubleValue() < MIN_TVL_USD) {
                continue;
            }
            List<String> chains = new ArrayList<>();
            Object chainsObj = item.get("chains");
            if (chainsObj instanceof List<?> list) {
                for (Object c : list) {
                    String s = str(c);
                    if (s != null) {
                        chains.add(s);
                    }
                }
            }
            out.add(new Protocol(slug, name, str(item.get("category")), chains, tvl,
                    dec(item.get("mcap")), dec(item.get("change_1d")), dec(item.get("change_7d"))));
        }
        return out;
    }

    @RateLimiter(name = "defillama")
    @Retry(name = "defillama")
    @CircuitBreaker(name = "defillama")
    @SuppressWarnings("unchecked")
    public List<Chain> fetchChains() {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) getJsonList("/v2/chains", "chains", LIST_TTL_MS);
        List<Chain> out = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String name = str(item.get("name"));
            BigDecimal tvl = dec(item.get("tvl"));
            if (name == null || tvl == null || tvl.doubleValue() < MIN_TVL_USD) {
                continue;
            }
            out.add(new Chain(name, tvl, dec(item.get("change_7d"))));
        }
        return out;
    }

    /** Protocol TVL history (ascending by date). Empty if slug unknown / no series. */
    @RateLimiter(name = "defillama")
    @Retry(name = "defillama")
    @CircuitBreaker(name = "defillama")
    @SuppressWarnings("unchecked")
    public List<HistoryPoint> fetchHistory(String slug) {
        if (slug == null || slug.isBlank()) {
            return List.of();
        }
        Map<String, Object> raw = (Map<String, Object>) getJson("/protocol/" + slug.trim(),
                "protocol:" + slug.trim(), HISTORY_TTL_MS, Map.class);
        Object tvlPoints = raw == null ? null : raw.get("tvl");
        List<HistoryPoint> out = new ArrayList<>();
        if (tvlPoints instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> pt)) {
                    continue;
                }
                Long date = lng(pt.get("date"));
                BigDecimal val = dec(pt.get("totalLiquidityUSD"));
                if (date == null || val == null) {
                    continue;
                }
                out.add(new HistoryPoint(date, val));
            }
        }
        out.sort((a, b) -> Long.compare(a.date(), b.date()));
        return out;
    }

    // ---- internal TTL cache + last-good fallback (defillama_service._get_json) ----

    private List<?> getJsonList(String path, String cacheKey, long ttlMs) {
        return (List<?>) getJson(path, cacheKey, ttlMs, List.class);
    }

    private synchronized Object getJson(String path, String cacheKey, long ttlMs, Class<?> type) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && entry.value != null && now < entry.expiresAt) {
            return entry.value;
        }
        Object data;
        try {
            data = restClient.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            if (entry != null && entry.lastGood != null) {
                return entry.lastGood; // serve stale on failure
            }
            throw new ProviderException("DefiLlama " + path + " failed", e);
        }
        if (data == null) {
            if (entry != null && entry.lastGood != null) {
                return entry.lastGood;
            }
            throw new ProviderException("DefiLlama " + path + " returned no data");
        }
        cache.put(cacheKey, new CacheEntry(data, data, now + ttlMs));
        return data;
    }

    private static final class CacheEntry {
        final Object value;
        final Object lastGood;
        final long expiresAt;

        CacheEntry(Object value, Object lastGood, long expiresAt) {
            this.value = value;
            this.lastGood = lastGood;
            this.expiresAt = expiresAt;
        }
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal dec(Object o) {
        if (o == null || "".equals(o)) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long lng(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
