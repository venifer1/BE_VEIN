package com.vein.supply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.market.provider.coingecko.CoinGeckoProvider;
import com.vein.market.provider.coingecko.CoinGeckoRateLimitException;

import lombok.extern.slf4j.Slf4j;

/**
 * Coin circulating-supply data (API_CONTRACT §6 / supply_tab.py). Serves the
 * latest CoinGecko {@code /coins/markets} snapshot filtered by {@code q}/sorted
 * by {@code sort}. On 429 the refresh backs off (Retry-After) and keeps the last
 * good snapshot rather than wiping it.
 */
@Service
@Slf4j
public class SupplyService {

    private static final int TARGET_ROWS = 300;
    private static final int PER_PAGE = 250;

    private final SupplySnapshotRepository repository;
    private final CoinGeckoProvider coinGecko;

    /** Earliest time the next refresh may run (set on 429). volatile: read by any thread. */
    private volatile Instant cooldownUntil = Instant.EPOCH;

    public SupplyService(SupplySnapshotRepository repository, CoinGeckoProvider coinGecko) {
        this.repository = repository;
        this.coinGecko = coinGecko;
    }

    /** Latest batch filtered by q (name/symbol/id contains) and sorted. */
    @Transactional(readOnly = true)
    public List<SupplyDto> list(String sort, String q) {
        String query = q == null ? null : q.trim().toLowerCase();
        List<SupplySnapshot> batch = repository.findLatestBatch();

        List<SupplySnapshot> filtered = new ArrayList<>();
        for (SupplySnapshot s : batch) {
            if (query != null && !query.isEmpty()) {
                if (!contains(s.getName(), query) && !contains(s.getSymbol(), query)
                        && !contains(s.getCoingeckoId(), query)) {
                    continue;
                }
            }
            filtered.add(s);
        }

        Comparator<SupplySnapshot> cmp = comparator(sort);
        filtered.sort(cmp);

        List<SupplyDto> rows = new ArrayList<>(filtered.size());
        for (SupplySnapshot s : filtered) {
            rows.add(new SupplyDto(
                    s.getRank(), s.getCoingeckoId(), s.getName(), s.getSymbol(),
                    plain(s.getPriceUsd()), plain(s.getMarketCap()), plain(s.getCirculating()),
                    plain(s.getTotalSupply()), plain(s.getMaxSupply()), plain(s.getCirculatingPct()),
                    plain(s.getFdv())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc().map(SupplySnapshot::getCollectedAt).orElse(null);
    }

    /**
     * Refresh from CoinGecko and persist a new batch. On 429 sets a cooldown and
     * returns without touching the existing snapshot (keep last good). On other
     * failures also keeps last good.
     */
    @Transactional
    public void refresh() {
        if (Instant.now().isBefore(cooldownUntil)) {
            log.debug("supply refresh skipped — CoinGecko cooldown until {}", cooldownUntil);
            return;
        }
        Instant now = Instant.now();
        List<CoinGeckoProvider.MarketCoin> coins;
        try {
            coins = coinGecko.fetchMarkets(TARGET_ROWS, PER_PAGE);
        } catch (CoinGeckoRateLimitException e) {
            cooldownUntil = now.plusSeconds(e.retryAfterSeconds());
            log.warn("supply refresh rate-limited; backing off until {}", cooldownUntil);
            return;
        } catch (RuntimeException e) {
            log.warn("supply refresh failed (keeping last good): {}", e.getMessage());
            return;
        }
        if (coins.isEmpty()) {
            return;
        }
        for (CoinGeckoProvider.MarketCoin c : coins) {
            BigDecimal circPct = circulatingPct(c.circulating(), c.maxSupply(), c.totalSupply());
            repository.save(SupplySnapshot.of(
                    c.id(), c.rank(), c.name(), c.symbol(), c.priceUsd(), c.marketCap(),
                    c.circulating(), c.totalSupply(), c.maxSupply(), circPct, c.fdv(), now));
        }
        repository.deleteByCollectedAtBefore(now);
        log.debug("supply refresh saved {} rows", coins.size());
    }

    /** circulating / (max or total) * 100 (supply_tab.py). */
    static BigDecimal circulatingPct(BigDecimal circulating, BigDecimal max, BigDecimal total) {
        if (circulating == null) {
            return null;
        }
        BigDecimal denom = (max != null && max.signum() > 0) ? max
                : (total != null && total.signum() > 0) ? total : null;
        if (denom == null) {
            return null;
        }
        return circulating.divide(denom, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private Comparator<SupplySnapshot> comparator(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            case "market_cap", "mcap" ->
                    nullsLastDesc(SupplySnapshot::getMarketCap);
            case "price" -> nullsLastDesc(SupplySnapshot::getPriceUsd);
            case "circulating_pct", "supply_pct" -> nullsLastDesc(SupplySnapshot::getCirculatingPct);
            case "fdv" -> nullsLastDesc(SupplySnapshot::getFdv);
            case "rank" -> Comparator.comparing(SupplySnapshot::getRank,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            // default: market_cap_rank ascending (legacy default ordering)
            default -> Comparator.comparing(SupplySnapshot::getRank,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private static Comparator<SupplySnapshot> nullsLastDesc(
            java.util.function.Function<SupplySnapshot, BigDecimal> f) {
        return Comparator.comparing(f, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    private static boolean contains(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    /** Validate sort eagerly so a bogus value is a 400 not a silent default (optional use). */
    public static void validateSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String key = sort.trim().toLowerCase();
        if (!List.of("market_cap", "mcap", "price", "circulating_pct", "supply_pct", "fdv", "rank")
                .contains(key)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "unsupported sort: " + sort);
        }
    }
}
