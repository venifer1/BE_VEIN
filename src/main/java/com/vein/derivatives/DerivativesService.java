package com.vein.derivatives;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.TimeUtil;
import com.vein.derivatives.DerivativesProvider.LongShortPoint;
import com.vein.derivatives.DerivativesProvider.OiHistPoint;
import com.vein.derivatives.DerivativesProvider.PremiumIndex;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.ProviderSymbol;
import com.vein.instrument.ProviderSymbolRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Derivatives (Binance USD-M perp) for {@code GET /derivatives} and
 * {@code GET /derivatives/{symbol}}. Matches the CRYPTO instruments we already have
 * to Binance perp symbols (BINANCE provider-symbol mapping if present, else
 * {@code <BASE>USDT}), enriches with mark/funding (one call) + per-symbol OI and
 * long/short ratio (rate-limited, individually try/caught). A ~60s in-memory TTL
 * cache spares the upstream; never throws on failure — returns empty + logs WARN.
 */
@Service
@Slf4j
public class DerivativesService {

    private static final String BINANCE = "BINANCE";
    private static final long TTL_MS = 60_000L;
    private static final int MAX_LIMIT = 50;
    /** Cap per-symbol fan-out (the instruments we have, ~20). */
    private static final int MAX_SYMBOLS = 30;
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;
    private final DerivativesProvider provider;

    private volatile List<Row> cache = null;
    private volatile long cacheAt = 0L;

    public DerivativesService(InstrumentRepository instrumentRepository,
                              ProviderSymbolRepository providerSymbolRepository,
                              DerivativesProvider provider) {
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
        this.provider = provider;
    }

    /** A derivatives row. Money/qty/rate values are plain BigDecimal strings. */
    public record Row(String symbol, String perp, String markPrice, String fundingRate,
                      String nextFundingAt, String openInterest, String oiValueUsd,
                      String longShortRatio) {
    }

    /** A {t, ratio} long/short history point ({@code t} is UTC ISO-8601). */
    public record LongShortHistoryPoint(String t, String ratio) {
    }

    /** A {t, oi} open-interest history point ({@code t} is UTC ISO-8601). */
    public record OiHistoryPoint(String t, String oi) {
    }

    /** Detail = the base row plus long/short and OI history series. */
    public record Detail(String symbol, String perp, String markPrice, String fundingRate,
                         String nextFundingAt, String openInterest, String oiValueUsd,
                         String longShortRatio, List<LongShortHistoryPoint> longShortHistory,
                         List<OiHistoryPoint> oiHistory) {
    }

    /** Last refresh time of the cached snapshot, or null. */
    public Instant lastCollectedAt() {
        return cache == null ? null : Instant.ofEpochMilli(cacheAt);
    }

    /** Top-{@code limit} rows by OI value (USD) desc. Empty on upstream failure. */
    public List<Row> list(int limit) {
        int n = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<Row> rows = snapshot();
        return rows.size() > n ? new ArrayList<>(rows.subList(0, n)) : rows;
    }

    /** Detail for one base symbol (e.g. {@code BTC}); null if unknown / no data. */
    public Detail detail(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String base = symbol.trim().toUpperCase();
        Row row = snapshot().stream()
                .filter(r -> base.equals(r.symbol()))
                .findFirst()
                .orElse(null);
        if (row == null) {
            return null;
        }
        List<LongShortHistoryPoint> lsHist = new ArrayList<>();
        try {
            for (LongShortPoint p : provider.fetchLongShortRatio(row.perp(), "1h", 24)) {
                lsHist.add(new LongShortHistoryPoint(
                        TimeUtil.toIso(Instant.ofEpochMilli(p.timestamp())),
                        p.longShortRatio() == null ? null : p.longShortRatio().toPlainString()));
            }
        } catch (RuntimeException e) {
            log.warn("derivatives long/short history failed for {}: {}", row.perp(), e.getMessage());
        }
        List<OiHistoryPoint> oiHist = new ArrayList<>();
        try {
            for (OiHistPoint p : provider.fetchOpenInterestHist(row.perp(), "1h", 24)) {
                oiHist.add(new OiHistoryPoint(
                        TimeUtil.toIso(Instant.ofEpochMilli(p.timestamp())),
                        p.openInterest() == null ? null : p.openInterest().toPlainString()));
            }
        } catch (RuntimeException e) {
            log.warn("derivatives OI history failed for {}: {}", row.perp(), e.getMessage());
        }
        return new Detail(row.symbol(), row.perp(), row.markPrice(), row.fundingRate(),
                row.nextFundingAt(), row.openInterest(), row.oiValueUsd(), row.longShortRatio(),
                lsHist, oiHist);
    }

    /** Cached snapshot of enriched rows (sorted by OI value desc). */
    private List<Row> snapshot() {
        long now = System.currentTimeMillis();
        List<Row> cached = cache;
        if (cached != null && (now - cacheAt) < TTL_MS) {
            return cached;
        }
        Map<String, PremiumIndex> premium;
        try {
            premium = provider.fetchPremiumIndex();
        } catch (RuntimeException e) {
            log.warn("derivatives snapshot unavailable — premiumIndex failed: {}", e.getMessage());
            return cached != null ? cached : List.of();
        }

        // base symbol -> perp, limited to the CRYPTO instruments we have. Order by
        // MAJORS first so the per-symbol OI fan-out (capped at MAX_SYMBOLS) always
        // covers BTC/ETH/etc. — otherwise the ~265-coin universe pushes majors out.
        Map<String, String> perpByBase = perpByBase();
        List<Map.Entry<String, String>> ordered = new ArrayList<>(perpByBase.entrySet());
        ordered.sort(Comparator.comparingInt((Map.Entry<String, String> en) -> priorityIndex(en.getKey()))
                .thenComparing(Map.Entry::getKey));
        List<Row> rows = new ArrayList<>();
        int processed = 0;
        for (Map.Entry<String, String> e : ordered) {
            if (processed >= MAX_SYMBOLS) {
                break;
            }
            String basis = e.getKey();
            String perp = e.getValue();
            PremiumIndex pi = premium.get(perp.toUpperCase());
            if (pi == null) {
                continue; // not a listed perp
            }
            processed++;

            BigDecimal oi = null;
            try {
                oi = provider.fetchOpenInterest(perp);
            } catch (RuntimeException ex) {
                log.warn("derivatives openInterest failed for {}: {}", perp, ex.getMessage());
            }
            BigDecimal oiValueUsd = (oi != null && pi.markPrice() != null)
                    ? oi.multiply(pi.markPrice(), MC) : null;

            BigDecimal lsRatio = null;
            try {
                List<LongShortPoint> ls = provider.fetchLongShortRatio(perp, "5m", 1);
                if (!ls.isEmpty()) {
                    lsRatio = ls.get(ls.size() - 1).longShortRatio();
                }
            } catch (RuntimeException ex) {
                log.warn("derivatives longShortRatio failed for {}: {}", perp, ex.getMessage());
            }

            rows.add(new Row(
                    basis,
                    perp,
                    plain(pi.markPrice()),
                    plain(pi.lastFundingRate()),
                    pi.nextFundingTime() == null ? null
                            : TimeUtil.toIso(Instant.ofEpochMilli(pi.nextFundingTime())),
                    plain(oi),
                    plain(oiValueUsd),
                    plain(lsRatio)));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> num(r.oiValueUsd())).reversed());
        cache = rows;
        cacheAt = now;
        return rows;
    }

    /**
     * base symbol (e.g. {@code BTC}) -> Binance perp (e.g. {@code BTCUSDT}) for the
     * CRYPTO instruments we have. Uses the BINANCE provider-symbol spot mapping
     * ({@code BTCUSDT}) when present, else {@code <BASE>USDT} derived from KRW-BASE.
     */
    @Transactional(readOnly = true)
    protected Map<String, String> perpByBase() {
        List<Instrument> instruments = instrumentRepository.findByMarketAndStatus("CRYPTO", "ACTIVE");
        Map<Long, String> binanceSpotByInstrument = new HashMap<>();
        for (ProviderSymbol ps : providerSymbolRepository.findByProvider(BINANCE)) {
            binanceSpotByInstrument.put(ps.getInstrumentId(), ps.getProviderSymbol());
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Instrument inst : instruments) {
            String symbol = inst.getSymbol();
            if (symbol == null || !symbol.startsWith("KRW-")) {
                continue;
            }
            String base = symbol.substring(4).toUpperCase();
            String mapped = binanceSpotByInstrument.get(inst.getId());
            // Binance spot symbol is already <BASE>USDT, which equals the USD-M perp.
            String perp = (mapped != null && !mapped.isBlank()) ? mapped.toUpperCase() : base + "USDT";
            out.putIfAbsent(base, perp);
        }
        return out;
    }

    /** Major perps processed first so the OI fan-out cap always covers them. */
    private static final List<String> MAJORS = List.of(
            "BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "ADA", "TRX", "AVAX", "LINK",
            "DOT", "BCH", "LTC", "ATOM", "NEAR", "APT", "SUI", "POL", "MATIC", "SHIB",
            "UNI", "ETC", "FIL", "ARB", "OP", "SAND", "AAVE", "INJ", "SEI", "TIA");

    private static int priorityIndex(String base) {
        int i = MAJORS.indexOf(base);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static double num(String s) {
        try {
            return s == null ? Double.NEGATIVE_INFINITY : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }
}
