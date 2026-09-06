package com.vein.market.provider.coingecko;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vein.market.provider.ProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * CoinGecko provider. {@code /global} feeds BTC/USDT dominance + a simple alt
 * index (terminal_tab.py). {@code /coins/markets} (supply/FDV) is exposed for
 * Phase 3 reuse. 429 responses surface {@code Retry-After} so resilience4j can
 * back off rather than hammer the public (keyless) endpoint.
 */
@Component
public class CoinGeckoProvider {

    public static final String CODE = "COINGECKO";

    private final RestClient restClient;

    public CoinGeckoProvider(
            @Value("${vein.coingecko.base-url:https://api.coingecko.com/api/v3}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    public String code() {
        return CODE;
    }

    /** Crypto market dominance gauges. */
    public record Dominance(BigDecimal btcDominance, BigDecimal usdtDominance, BigDecimal altIndex) {
    }

    /**
     * Global crypto market snapshot from {@code /global} (USD totals + dominance).
     * Any field may be null when absent upstream. Money/percent values are BigDecimal
     * (surfaced as Strings by callers).
     */
    public record GlobalSnapshot(BigDecimal totalMarketCapUsd, BigDecimal totalVolumeUsd,
                                 BigDecimal marketCapChange24hPct, Integer activeCryptos,
                                 BigDecimal btcDominance, BigDecimal ethDominance) {
    }

    /** Single keyless {@code /global} fetch shared by dominance + global snapshot. */
    private GlobalResponse fetchGlobalRaw() {
        try {
            return restClient.get()
                    .uri("/global")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 429) {
                            throw new ProviderException("CoinGecko rate limited (429), Retry-After="
                                    + res.getHeaders().getFirst("Retry-After"));
                        }
                        throw new ProviderException("CoinGecko /global HTTP " + res.getStatusCode());
                    })
                    .body(GlobalResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("CoinGecko /global failed", e);
        }
    }

    @RateLimiter(name = "coingecko")
    @Retry(name = "coingecko")
    @CircuitBreaker(name = "coingecko")
    public Dominance fetchDominance() {
        GlobalResponse body = fetchGlobalRaw();
        if (body == null || body.data() == null || body.data().marketCapPercentage() == null) {
            throw new ProviderException("CoinGecko /global returned no dominance data");
        }
        Map<String, BigDecimal> pct = body.data().marketCapPercentage();
        BigDecimal btc = pct.get("btc");
        BigDecimal usdt = pct.get("usdt");
        if (btc == null || usdt == null) {
            throw new ProviderException("CoinGecko /global missing btc/usdt dominance");
        }
        // alt index (simple): 100 - btc - usdt, clamped to [0,100] (terminal_tab.py).
        BigDecimal alt = BigDecimal.valueOf(100).subtract(btc).subtract(usdt);
        if (alt.signum() < 0) {
            alt = BigDecimal.ZERO;
        } else if (alt.compareTo(BigDecimal.valueOf(100)) > 0) {
            alt = BigDecimal.valueOf(100);
        }
        return new Dominance(btc, usdt, alt);
    }

    /**
     * Global crypto market totals + BTC/ETH dominance from the keyless {@code /global}
     * endpoint (reuses the same HTTP call as {@link #fetchDominance()}). Throws
     * {@link ProviderException} on upstream failure so callers can keep last-good.
     */
    @RateLimiter(name = "coingecko")
    @Retry(name = "coingecko")
    @CircuitBreaker(name = "coingecko")
    public GlobalSnapshot fetchGlobal() {
        GlobalResponse body = fetchGlobalRaw();
        if (body == null || body.data() == null) {
            throw new ProviderException("CoinGecko /global returned no data");
        }
        GlobalResponse.Data d = body.data();
        Map<String, BigDecimal> mcap = d.totalMarketCap();
        Map<String, BigDecimal> vol = d.totalVolume();
        Map<String, BigDecimal> pct = d.marketCapPercentage();
        return new GlobalSnapshot(
                mcap == null ? null : mcap.get("usd"),
                vol == null ? null : vol.get("usd"),
                d.marketCapChange24hUsd(),
                d.activeCryptocurrencies(),
                pct == null ? null : pct.get("btc"),
                pct == null ? null : pct.get("eth"));
    }

    /** A {@code /coins/markets} row (supply_tab.py columns). Money/supply as BigDecimal. */
    public record MarketCoin(String id, String name, String symbol, Integer rank,
                             BigDecimal priceUsd, BigDecimal marketCap, BigDecimal fdv,
                             BigDecimal circulating, BigDecimal totalSupply, BigDecimal maxSupply) {
    }

    /**
     * Fetch the top {@code target} coins by market cap from {@code /coins/markets},
     * paging at {@code perPage} (max 250). On HTTP 429 throws
     * {@link CoinGeckoRateLimitException} carrying Retry-After so callers can back
     * off and keep their last good snapshot (supply_tab._fetch_supply_rows).
     */
    @RateLimiter(name = "coingecko")
    @Retry(name = "coingecko")
    @CircuitBreaker(name = "coingecko")
    public List<MarketCoin> fetchMarkets(int target, int perPage) {
        int per = Math.max(1, Math.min(perPage, 250));
        int want = Math.max(1, target);
        int pages = (want + per - 1) / per;
        List<MarketCoin> out = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            int pageNo = page;
            CoinMarketResponse[] body;
            try {
                body = restClient.get()
                        .uri(b -> b.path("/coins/markets")
                                .queryParam("vs_currency", "usd")
                                .queryParam("order", "market_cap_desc")
                                .queryParam("per_page", per)
                                .queryParam("page", pageNo)
                                .queryParam("sparkline", false)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            if (res.getStatusCode().value() == 429) {
                                throw new CoinGeckoRateLimitException(
                                        parseRetryAfter(res.getHeaders().getFirst("Retry-After")));
                            }
                            throw new ProviderException("CoinGecko /coins/markets HTTP " + res.getStatusCode());
                        })
                        .body(CoinMarketResponse[].class);
            } catch (RestClientException e) {
                throw new ProviderException("CoinGecko /coins/markets failed", e);
            }
            if (body == null || body.length == 0) {
                break;
            }
            for (CoinMarketResponse c : body) {
                if (c == null || c.id() == null) {
                    continue;
                }
                out.add(new MarketCoin(
                        c.id(),
                        c.name(),
                        c.symbol() == null ? null : c.symbol().toUpperCase(),
                        c.marketCapRank(),
                        c.currentPrice(),
                        c.marketCap(),
                        c.fullyDilutedValuation(),
                        c.circulatingSupply(),
                        c.totalSupply(),
                        c.maxSupply()));
                if (out.size() >= want) {
                    return out;
                }
            }
            if (body.length < per) {
                break;
            }
        }
        return out;
    }

    /**
     * A {@code /search/trending} coin row. {@code priceBtc} is BigDecimal (money/qty
     * surfaced as a String upstream). Order reflects CoinGecko's trending rank.
     */
    public record TrendingCoin(String id, String symbol, String name, Integer marketCapRank,
                               String thumb, BigDecimal priceBtc) {
    }

    /**
     * Fetch the current trending coins from {@code /search/trending} (keyless). On
     * HTTP 429 throws {@link CoinGeckoRateLimitException} carrying Retry-After so
     * callers can back off and keep their last-good snapshot.
     */
    @RateLimiter(name = "coingecko")
    @Retry(name = "coingecko")
    @CircuitBreaker(name = "coingecko")
    public List<TrendingCoin> fetchTrending() {
        TrendingResponse body;
        try {
            body = restClient.get()
                    .uri("/search/trending")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 429) {
                            throw new CoinGeckoRateLimitException(
                                    parseRetryAfter(res.getHeaders().getFirst("Retry-After")));
                        }
                        throw new ProviderException("CoinGecko /search/trending HTTP " + res.getStatusCode());
                    })
                    .body(TrendingResponse.class);
        } catch (RestClientException e) {
            throw new ProviderException("CoinGecko /search/trending failed", e);
        }
        if (body == null || body.coins() == null) {
            return List.of();
        }
        List<TrendingCoin> out = new ArrayList<>(body.coins().size());
        for (TrendingResponse.Coin coin : body.coins()) {
            if (coin == null || coin.item() == null || coin.item().id() == null) {
                continue;
            }
            TrendingResponse.Item it = coin.item();
            out.add(new TrendingCoin(
                    it.id(),
                    it.symbol() == null ? null : it.symbol().toUpperCase(),
                    it.name(),
                    it.marketCapRank(),
                    it.thumb(),
                    it.data() == null ? null : it.data().priceBtc()));
        }
        return out;
    }

    private static int parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return 120;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(header.trim()), 900));
        } catch (NumberFormatException e) {
            return 120;
        }
    }
}
