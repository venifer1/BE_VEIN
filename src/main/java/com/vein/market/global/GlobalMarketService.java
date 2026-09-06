package com.vein.market.global;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.vein.market.provider.coingecko.CoinGeckoProvider;
import com.vein.market.provider.coingecko.CoinGeckoProvider.GlobalSnapshot;

import lombok.extern.slf4j.Slf4j;

/**
 * Global crypto market snapshot for {@code GET /market/global}, sourced from CoinGecko's
 * keyless {@code /global} endpoint. A ~60s in-memory TTL cache spares the rate-limited
 * public endpoint; on upstream failure the last-good snapshot is kept and {@code null} is
 * served until one is available — the call never throws.
 */
@Service
@Slf4j
public class GlobalMarketService {

    private static final long TTL_MS = 60_000L;

    private final CoinGeckoProvider coinGecko;

    private volatile GlobalRow cache = null;
    private volatile long cacheAt = 0L;

    public GlobalMarketService(CoinGeckoProvider coinGecko) {
        this.coinGecko = coinGecko;
    }

    /** A global market row. Money/percent values are plain BigDecimal strings. */
    public record GlobalRow(String totalMarketCapUsd, String totalVolumeUsd,
                            String marketCapChange24hPct, Integer activeCryptos,
                            String btcDominance, String ethDominance) {
    }

    /** Cached global snapshot; refreshed every {@link #TTL_MS}. Null on failure with no prior snapshot. */
    public GlobalRow global() {
        long now = System.currentTimeMillis();
        GlobalRow cached = cache;
        if (cached != null && (now - cacheAt) < TTL_MS) {
            return cached;
        }
        GlobalSnapshot s;
        try {
            s = coinGecko.fetchGlobal();
        } catch (RuntimeException e) {
            log.warn("global market snapshot unavailable: {}", e.getMessage());
            return cached;
        }
        GlobalRow row = new GlobalRow(
                str(s.totalMarketCapUsd()),
                str(s.totalVolumeUsd()),
                str(s.marketCapChange24hPct()),
                s.activeCryptos(),
                str(s.btcDominance()),
                str(s.ethDominance()));
        cache = row;
        cacheAt = now;
        return row;
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
