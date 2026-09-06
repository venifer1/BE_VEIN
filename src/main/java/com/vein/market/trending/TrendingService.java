package com.vein.market.trending;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vein.market.provider.coingecko.CoinGeckoProvider;
import com.vein.market.provider.coingecko.CoinGeckoProvider.TrendingCoin;

import lombok.extern.slf4j.Slf4j;

/**
 * Trending coins for {@code GET /market/trending}, sourced from CoinGecko's keyless
 * {@code /search/trending}. A ~5 min in-memory TTL cache spares the rate-limited
 * public endpoint; on upstream failure the last-good snapshot is kept and an empty
 * list is served until one is available — the call never throws.
 */
@Service
@Slf4j
public class TrendingService {

    private static final long TTL_MS = 300_000L;

    private final CoinGeckoProvider coinGecko;

    private volatile List<TrendingRow> cache = null;
    private volatile long cacheAt = 0L;

    public TrendingService(CoinGeckoProvider coinGecko) {
        this.coinGecko = coinGecko;
    }

    /** A trending row. {@code priceBtc} is a plain BigDecimal string. */
    public record TrendingRow(int rank, String coingeckoId, String symbol, String name,
                              Integer marketCapRank, String thumb, String priceBtc) {
    }

    /** Cached trending list; refreshed every {@link #TTL_MS}. Empty on failure with no prior snapshot. */
    public List<TrendingRow> trending() {
        long now = System.currentTimeMillis();
        List<TrendingRow> cached = cache;
        if (cached != null && (now - cacheAt) < TTL_MS) {
            return cached;
        }
        List<TrendingCoin> coins;
        try {
            coins = coinGecko.fetchTrending();
        } catch (RuntimeException e) {
            log.warn("trending snapshot unavailable: {}", e.getMessage());
            return cached != null ? cached : List.of();
        }
        List<TrendingRow> rows = new ArrayList<>(coins.size());
        int rank = 1;
        for (TrendingCoin c : coins) {
            rows.add(new TrendingRow(
                    rank++,
                    c.id(),
                    c.symbol(),
                    c.name(),
                    c.marketCapRank(),
                    c.thumb(),
                    c.priceBtc() == null ? null : c.priceBtc().toPlainString()));
        }
        cache = rows;
        cacheAt = now;
        return rows;
    }
}
