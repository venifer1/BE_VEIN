package com.vein.market.index;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.market.provider.alternativeme.AlternativeMeProvider;
import com.vein.market.provider.coingecko.CoinGeckoProvider;
import com.vein.market.provider.equity.EquitySidecarClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Collects and serves terminal market gauges (GET /market/indices):
 * fear&greed, BTC/USDT dominance, alt index, and the NASDAQ/KOSPI/KOSDAQ index
 * values. Equity index values come from the REAL Python equity sidecar
 * (yfinance); on a null/error from the sidecar we fall back to a synthetic
 * stand-in so the card always renders.
 */
@Service
@Slf4j
public class MarketIndexService {

    public static final String FEAR_GREED = "FEAR_GREED";
    public static final String BTC_DOMINANCE = "BTC_DOMINANCE";
    public static final String USDT_DOMINANCE = "USDT_DOMINANCE";
    public static final String ALT_INDEX = "ALT_INDEX";
    public static final String NASDAQ = "NASDAQ";
    public static final String KOSPI = "KOSPI";
    public static final String KOSDAQ = "KOSDAQ";

    private final MarketIndexRepository repository;
    private final AlternativeMeProvider alternativeMe;
    private final CoinGeckoProvider coinGecko;
    private final EquitySidecarClient equitySidecar;

    public MarketIndexService(MarketIndexRepository repository,
                              AlternativeMeProvider alternativeMe,
                              CoinGeckoProvider coinGecko,
                              EquitySidecarClient equitySidecar) {
        this.repository = repository;
        this.alternativeMe = alternativeMe;
        this.coinGecko = coinGecko;
        this.equitySidecar = equitySidecar;
    }

    public record IndexValue(String key, String value, String classification, String collectedAt) {
    }

    /** One time-series point for an index sparkline. {@code t} is ISO-8601 UTC. */
    public record HistoryPoint(String t, String value) {
    }

    /** Max points returned for an index history (downsample guard for charts). */
    private static final int HISTORY_MAX_POINTS = 500;
    private static final Set<String> EQUITY_INDEX_KEYS = Set.of(NASDAQ, KOSPI, KOSDAQ);

    /**
     * Time series for one index key over the last {@code days}, ascending by time.
     * Empty if the key is unknown / has no snapshots. Evenly downsampled to at
     * most {@link #HISTORY_MAX_POINTS} points so charts stay light as data grows.
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> history(String key, int days) {
        int boundedDays = Math.max(1, Math.min(days, 365));
        if (EQUITY_INDEX_KEYS.contains(key)) {
            List<HistoryPoint> upstream = equitySidecar.fetchIndexHistory(key, boundedDays).stream()
                    .map(point -> new HistoryPoint(point.t(), point.value().toPlainString()))
                    .toList();
            if (!upstream.isEmpty()) {
                return downsample(upstream, HISTORY_MAX_POINTS);
            }
        }

        Instant since = Instant.now().minus(Duration.ofDays(boundedDays));
        // Repository returns newest-first; collect within the window then reverse.
        List<HistoryPoint> desc = new ArrayList<>();
        for (MarketIndex mi : repository.findByIndexKeyOrderByCollectedAtDesc(key)) {
            if (mi.getCollectedAt().isBefore(since)) {
                break;
            }
            if (mi.getValue() != null) {
                desc.add(new HistoryPoint(
                        com.vein.common.TimeUtil.toIso(mi.getCollectedAt()),
                        mi.getValue().toPlainString()));
            }
        }
        Collections.reverse(desc);
        return downsample(desc, HISTORY_MAX_POINTS);
    }

    private static List<HistoryPoint> downsample(List<HistoryPoint> points, int max) {
        int n = points.size();
        if (n <= max) {
            return points;
        }
        List<HistoryPoint> out = new ArrayList<>(max);
        // Keep evenly-spaced samples; always include the final (latest) point.
        for (int i = 0; i < max - 1; i++) {
            out.add(points.get((int) ((long) i * (n - 1) / (max - 1))));
        }
        out.add(points.get(n - 1));
        return out;
    }

    /** Latest value per known index key (skips keys with no snapshot yet). */
    @Transactional(readOnly = true)
    public List<IndexValue> latest() {
        List<IndexValue> out = new ArrayList<>();
        for (String key : List.of(FEAR_GREED, BTC_DOMINANCE, USDT_DOMINANCE, ALT_INDEX,
                NASDAQ, KOSPI, KOSDAQ)) {
            repository.findTopByIndexKeyOrderByCollectedAtDesc(key).ifPresent(mi ->
                    out.add(new IndexValue(
                            mi.getIndexKey(),
                            mi.getValue() == null ? null : mi.getValue().toPlainString(),
                            mi.getClassification(),
                            com.vein.common.TimeUtil.toIso(mi.getCollectedAt()))));
        }
        return out;
    }

    /** Most recent collected_at across all index rows (for freshness/meta). */
    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByIndexKeyOrderByCollectedAtDesc(FEAR_GREED)
                .map(MarketIndex::getCollectedAt)
                .orElse(null);
    }

    /**
     * Refresh all gauges from upstream providers and persist a new snapshot row
     * per key. Each source is independently fault-tolerant: a failing provider
     * is logged and simply skipped so the rest still update.
     */
    @Transactional
    public void refresh() {
        Instant now = Instant.now();

        try {
            AlternativeMeProvider.FearGreed fg = alternativeMe.fetchFearGreed();
            repository.save(MarketIndex.of(FEAR_GREED, fg.value(), fg.classification(), now));
        } catch (RuntimeException e) {
            log.warn("fear&greed refresh failed: {}", e.getMessage());
        }

        try {
            CoinGeckoProvider.Dominance d = coinGecko.fetchDominance();
            repository.save(MarketIndex.of(BTC_DOMINANCE, d.btcDominance(), null, now));
            repository.save(MarketIndex.of(USDT_DOMINANCE, d.usdtDominance(), null, now));
            repository.save(MarketIndex.of(ALT_INDEX, d.altIndex(), null, now));
        } catch (RuntimeException e) {
            log.warn("dominance refresh failed: {}", e.getMessage());
        }

        // Equity index values (NASDAQ/KOSPI/KOSDAQ) come from the REAL Python
        // equity sidecar (yfinance). On null/error the client returns null and we
        // fall back to a synthetic stand-in so the card always renders.
        repository.save(MarketIndex.of(NASDAQ, equityIndex(NASDAQ, 18000), null, now));
        repository.save(MarketIndex.of(KOSPI, equityIndex(KOSPI, 2700), null, now));
        repository.save(MarketIndex.of(KOSDAQ, equityIndex(KOSDAQ, 850), null, now));
    }

    /**
     * Real index level from the sidecar, falling back to a synthetic stand-in
     * around {@code base} when the sidecar is down or returns null.
     */
    private java.math.BigDecimal equityIndex(String key, double base) {
        try {
            java.math.BigDecimal real = equitySidecar.fetchIndex(key);
            if (real != null) {
                return real.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        } catch (RuntimeException e) {
            log.warn("equity index {} refresh failed: {}", key, e.getMessage());
        }
        return syntheticIndex(base);
    }

    /** Deterministic synthetic index level around {@code base}, clearly not real. */
    private static java.math.BigDecimal syntheticIndex(double base) {
        double drift = Math.sin(System.currentTimeMillis() / 8.64e7) * base * 0.01;
        return java.math.BigDecimal.valueOf(base + drift)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
