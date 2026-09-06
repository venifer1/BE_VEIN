package com.vein.market;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.common.Freshness;
import com.vein.market.feargreed.FearGreedService;
import com.vein.market.feargreed.FearGreedService.HistoryRow;
import com.vein.market.global.GlobalMarketService;
import com.vein.market.global.GlobalMarketService.GlobalRow;
import com.vein.market.index.MarketIndexService;
import com.vein.market.index.MarketIndexService.HistoryPoint;
import com.vein.market.index.MarketIndexService.IndexValue;
import com.vein.market.kimchi.KimchiPremiumService;
import com.vein.market.kimchi.KimchiPremiumService.Row;
import com.vein.market.movers.MoversService;
import com.vein.market.movers.MoversService.MoverRow;
import com.vein.market.trending.TrendingService;
import com.vein.market.trending.TrendingService.TrendingRow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Terminal (home) market endpoints (API_CONTRACT §2): market indices and kimchi
 * premium. Both serve the cached snapshots persisted by the schedulers; freshness
 * is reported in {@code meta}.
 */
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market", description = "Terminal market indices & kimchi premium")
public class MarketController {

    /** Indices refresh ~60s; consider stale at > 5 min. */
    private static final Duration INDEX_FRESH_WINDOW = Duration.ofMinutes(5);
    /** Kimchi refresh ~60s; consider stale at > 3 min. */
    private static final Duration KIMCHI_FRESH_WINDOW = Duration.ofMinutes(3);

    private final MarketIndexService indexService;
    private final KimchiPremiumService kimchiService;
    private final FearGreedService fearGreedService;
    private final MoversService moversService;
    private final TrendingService trendingService;
    private final GlobalMarketService globalMarketService;

    public MarketController(MarketIndexService indexService, KimchiPremiumService kimchiService,
                            FearGreedService fearGreedService, MoversService moversService,
                            TrendingService trendingService, GlobalMarketService globalMarketService) {
        this.indexService = indexService;
        this.kimchiService = kimchiService;
        this.fearGreedService = fearGreedService;
        this.moversService = moversService;
        this.trendingService = trendingService;
        this.globalMarketService = globalMarketService;
    }

    @GetMapping("/indices")
    @Operation(summary = "Market indices",
            description = "Fear&Greed (+classification), BTC/USDT dominance, alt index, "
                    + "NASDAQ/KOSPI/KOSDAQ index values. Cached snapshots; freshness in meta.")
    public ApiResponse<List<IndexValue>> indices() {
        List<IndexValue> data = indexService.latest();
        String freshness = freshness(indexService.lastCollectedAt(), INDEX_FRESH_WINDOW).name();
        return ApiResponse.of(data, freshness);
    }

    @GetMapping("/indices/{key}/history")
    @Operation(summary = "Index history (sparkline)",
            description = "Time series for one index key (FEAR_GREED | BTC_DOMINANCE | "
                    + "USDT_DOMINANCE | ALT_INDEX | NASDAQ | KOSPI | KOSDAQ), ascending by time. "
                    + "?days=1..365 (default 30). Empty for unknown keys.")
    public ApiResponse<List<HistoryPoint>> indexHistory(
            @PathVariable String key,
            @RequestParam(required = false, defaultValue = "30") int days) {
        return ApiResponse.of(indexService.history(key.toUpperCase(), days));
    }

    @GetMapping("/kimchi-premium")
    @Operation(summary = "Kimchi premium",
            description = "Upbit(KRW) vs Binance(USDT) x USD/KRW premium per coin. "
                    + "BTC/ETH/XRP pinned on top; ?sort=premium_desc|premium_asc.")
    public ApiResponse<List<Row>> kimchiPremium(@RequestParam(required = false) String sort) {
        List<Row> data = kimchiService.latest(sort);
        String freshness = freshness(kimchiService.lastCollectedAt(), KIMCHI_FRESH_WINDOW).name();
        return ApiResponse.of(data, freshness);
    }

    @GetMapping("/fear-greed/history")
    @Operation(summary = "Fear & Greed history (sparkline)",
            description = "Crypto Fear&Greed daily history (alternative.me), ascending by date. "
                    + "?days=1..365 (default 30). Cached ~10 min; empty data on upstream failure.")
    public ApiResponse<List<HistoryRow>> fearGreedHistory(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return ApiResponse.of(fearGreedService.history(days));
    }

    @GetMapping("/movers")
    @Operation(summary = "Movers (gainers / losers / volume)",
            description = "?market=CRYPTO (Upbit KRW) | US | KOSPI | KOSDAQ. Equities movers are "
                    + "computed from the last two daily candles in the DB (no external call). "
                    + "?type=GAINERS|LOSERS|VOLUME (default GAINERS), ?limit=1..50 (default 20). "
                    + "CRYPTO cached ~30s, equities ~60s; empty data on upstream failure.")
    public ApiResponse<List<MoverRow>> movers(
            @RequestParam(required = false, defaultValue = "CRYPTO") String market,
            @RequestParam(required = false, defaultValue = "GAINERS") String type,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ApiResponse.of(moversService.movers(market, type, limit));
    }

    @GetMapping("/trending")
    @Operation(summary = "Trending coins",
            description = "CoinGecko /search/trending (keyless), ranked. Each row carries "
                    + "coingecko_id, symbol, name, market_cap_rank, thumb, price_btc. "
                    + "Cached ~5 min (keeps last-good on upstream failure); empty data otherwise.")
    public ApiResponse<List<TrendingRow>> trending() {
        return ApiResponse.of(trendingService.trending());
    }

    @GetMapping("/global")
    @Operation(summary = "Global crypto market snapshot",
            description = "CoinGecko /global (keyless): total market cap (USD), total volume (USD), "
                    + "24h market-cap change %, active cryptocurrencies, BTC/ETH dominance. "
                    + "Cached ~60s (keeps last-good on upstream failure); null data otherwise.")
    public ApiResponse<GlobalRow> global() {
        return ApiResponse.of(globalMarketService.global());
    }

    private static Freshness freshness(Instant collectedAt, Duration window) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(window) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
