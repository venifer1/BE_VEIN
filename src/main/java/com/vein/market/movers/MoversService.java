package com.vein.market.movers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.market.provider.upbit.UpbitMarketDataProvider;
import com.vein.market.provider.upbit.UpbitTickerResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Movers (급등/급락/거래량 급증 — legacy terminal feature) for
 * {@code GET /market/movers}. CRYPTO is sourced from Upbit's all-KRW ticker;
 * other markets return an empty list for now (equities movers later).
 * A ~30s in-memory TTL cache spares Upbit; never throws on upstream failure.
 */
@Service
@Slf4j
public class MoversService {

    private static final long TTL_MS = 30_000L;
    /** Equities movers are computed from daily candles in the DB; cache per market ~60s. */
    private static final long EQUITY_TTL_MS = 60_000L;
    private static final int MAX_LIMIT = 50;
    private static final String DAILY = "1d";
    /** signed_change_rate is a fraction (0.0523 = +5.23%); expose as percent. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InstrumentRepository instrumentRepository;
    private final CandleRepository candleRepository;
    private final UpbitMarketDataProvider upbit;

    /** Cached snapshot of all KRW ticker rows mapped to MoverRow (unsorted). */
    private volatile List<MoverRow> cache = null;
    private volatile long cacheAt = 0L;

    /** Per-market equity snapshots (US/KOSPI/KOSDAQ), unsorted. */
    private final Map<String, List<MoverRow>> equityCache = new ConcurrentHashMap<>();
    private final Map<String, Long> equityCacheAt = new ConcurrentHashMap<>();

    public MoversService(InstrumentRepository instrumentRepository,
                         CandleRepository candleRepository, UpbitMarketDataProvider upbit) {
        this.instrumentRepository = instrumentRepository;
        this.candleRepository = candleRepository;
        this.upbit = upbit;
    }

    /**
     * A movers row. Money/rate values are plain BigDecimal strings. {@code instrumentId}
     * is the instruments-table id (for deep-linking), null when not resolvable.
     */
    public record MoverRow(String symbol, String name, String price,
                           String changeRate, String tradeValue24h, Long instrumentId) {
    }

    /**
     * Movers for {@code market} ({@code CRYPTO} only) of {@code type}
     * (GAINERS|LOSERS|VOLUME), capped at {@code limit}. Empty on failure or for
     * non-crypto markets.
     */
    private static final Set<String> EQUITY_MARKETS = Set.of("US", "KOSPI", "KOSDAQ");

    public List<MoverRow> movers(String market, String type, int limit) {
        String m = market == null ? "CRYPTO" : market.trim().toUpperCase();
        int n = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String t = type == null ? "GAINERS" : type.trim().toUpperCase();

        List<MoverRow> rows;
        if (EQUITY_MARKETS.contains(m)) {
            rows = equitySnapshot(m);
        } else if ("CRYPTO".equals(m)) {
            rows = snapshot();
        } else {
            return List.of();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        List<MoverRow> sorted = new ArrayList<>(rows);
        switch (t) {
            case "LOSERS" -> sorted.sort(Comparator.comparingDouble(r -> num(r.changeRate())));
            case "VOLUME" -> sorted.sort(Comparator.comparingDouble((MoverRow r) ->
                    num(r.tradeValue24h())).reversed());
            // GAINERS (default): top signed change rate desc.
            default -> sorted.sort(Comparator.comparingDouble((MoverRow r) ->
                    num(r.changeRate())).reversed());
        }
        return sorted.size() > n ? new ArrayList<>(sorted.subList(0, n)) : sorted;
    }

    /** Cached all-KRW snapshot mapped to rows; refreshed every {@link #TTL_MS}. */
    private List<MoverRow> snapshot() {
        long now = System.currentTimeMillis();
        List<MoverRow> cached = cache;
        if (cached != null && (now - cacheAt) < TTL_MS) {
            return cached;
        }
        List<UpbitTickerResponse> tickers;
        try {
            tickers = upbit.fetchAllKrwTickers();
        } catch (RuntimeException e) {
            log.warn("movers snapshot unavailable: {}", e.getMessage());
            return cached != null ? cached : List.of();
        }
        Map<String, String> nameBySymbol = nameBySymbol();
        Map<String, Long> idBySymbol = cryptoIdBySymbol();
        List<MoverRow> rows = new ArrayList<>(tickers.size());
        for (UpbitTickerResponse t : tickers) {
            if (t.market() == null) {
                continue;
            }
            BigDecimal changePct = t.signedChangeRate() == null ? null
                    : t.signedChangeRate().multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
            rows.add(new MoverRow(
                    t.market(),
                    nameBySymbol.get(t.market()),
                    t.tradePrice() == null ? null : t.tradePrice().toPlainString(),
                    changePct == null ? null : changePct.toPlainString(),
                    t.accTradePrice24h() == null ? null : t.accTradePrice24h().toPlainString(),
                    idBySymbol.get(t.market())));
        }
        cache = rows;
        cacheAt = now;
        return rows;
    }

    /**
     * Per-market equity snapshot (US/KOSPI/KOSDAQ) computed purely from the daily
     * candles already persisted in the DB — no external call. For each ACTIVE
     * instrument we read its last two {@code 1d} candles; with both present,
     * {@code price}=latest close, {@code change_rate}=(latest/prev−1)×100, and
     * {@code trade_value24h}=latest close × latest volume (shares, approx).
     * Instruments with fewer than two candles are skipped. Cached ~60s per market.
     */
    @Transactional(readOnly = true)
    protected List<MoverRow> equitySnapshot(String market) {
        long now = System.currentTimeMillis();
        List<MoverRow> cached = equityCache.get(market);
        Long at = equityCacheAt.get(market);
        if (cached != null && at != null && (now - at) < EQUITY_TTL_MS) {
            return cached;
        }
        List<MoverRow> rows = new ArrayList<>();
        try {
            for (Instrument inst : instrumentRepository.findByMarketAndStatus(market, "ACTIVE")) {
                List<Candle> last2 = candleRepository
                        .findTop2ByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(
                                inst.getId(), DAILY);
                if (last2.size() < 2) {
                    continue;
                }
                Candle latest = last2.get(0);
                Candle prev = last2.get(1);
                BigDecimal close = latest.getClose();
                BigDecimal prevClose = prev.getClose();
                if (close == null || prevClose == null || prevClose.signum() == 0) {
                    continue;
                }
                BigDecimal changePct = close.divide(prevClose, 10, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
                BigDecimal tradeValue = latest.getVolume() == null ? null
                        : close.multiply(latest.getVolume());
                rows.add(new MoverRow(
                        inst.getSymbol(),
                        inst.getName(),
                        close.toPlainString(),
                        changePct.toPlainString(),
                        tradeValue == null ? null : tradeValue.toPlainString(),
                        inst.getId()));
            }
        } catch (RuntimeException e) {
            log.warn("equity movers snapshot unavailable for {}: {}", market, e.getMessage());
            return cached != null ? cached : List.of();
        }
        equityCache.put(market, rows);
        equityCacheAt.put(market, now);
        return rows;
    }

    @Transactional(readOnly = true)
    protected Map<String, String> nameBySymbol() {
        Map<String, String> out = new HashMap<>();
        // Base: Upbit /v1/market/all Korean names cover ALL ~200 KRW markets
        // (so obscure movers aren't symbol-only). Best-effort.
        try {
            for (var info : upbit.listInstruments()) {
                if (info.symbol() != null && info.koreanName() != null) {
                    out.put(info.symbol(), info.koreanName());
                }
            }
        } catch (RuntimeException e) {
            log.warn("movers name map (upbit) unavailable: {}", e.getMessage());
        }
        // Overlay our curated instrument-table names where present.
        for (Instrument i : instrumentRepository.findByMarket("CRYPTO")) {
            if (i.getSymbol() != null && i.getName() != null) {
                out.put(i.getSymbol(), i.getName());
            }
        }
        return out;
    }

    /**
     * Crypto instrument ids keyed by symbol ({@code KRW-XXX}), for movers deep-linking.
     * Resolved from the instruments table the same way {@link #nameBySymbol()} does;
     * symbols absent from the table simply have no id (null in the row). Best-effort.
     */
    @Transactional(readOnly = true)
    protected Map<String, Long> cryptoIdBySymbol() {
        Map<String, Long> out = new HashMap<>();
        for (Instrument i : instrumentRepository.findByMarket("CRYPTO")) {
            if (i.getSymbol() != null && i.getId() != null) {
                out.put(i.getSymbol(), i.getId());
            }
        }
        return out;
    }

    private static double num(String s) {
        try {
            return s == null ? Double.NEGATIVE_INFINITY : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }
}
