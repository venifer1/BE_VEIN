package com.vein.theme;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.supply.SupplySnapshot;
import com.vein.supply.SupplySnapshotRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Theme/sector classification (API_CONTRACT §6 / theme_sector_tab.py). Builds a
 * persisted {@code themes} + {@code theme_constituents} model from:
 *   1. the legacy JSON maps (manual_json, high confidence) when present, and
 *   2. a keyword heuristic fallback (lower confidence) for the rest.
 * CRYPTO constituents come from the latest supply snapshot (or a known seed set);
 * US/KR come from the instruments table. Seeding is idempotent — themes are keyed
 * by (market, name) and re-seeding clears constituents first.
 */
@Service
@Slf4j
public class ThemeService {

    public static final String CRYPTO = "CRYPTO";
    public static final String US = "US";
    public static final String KR = "KR";

    /** Confidence below this is "low confidence" for the filter. */
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.50");
    /** Heuristic / fallback confidence when no manual map entry exists. */
    private static final BigDecimal HEURISTIC_CONFIDENCE = new BigDecimal("0.40");
    private static final String UNCLASSIFIED = "미분류";

    /** Known crypto symbols (fallback seed when no supply snapshot exists). */
    private static final List<String> SEED_COINS = List.of(
            "BTC", "ETH", "XRP", "SOL", "DOGE", "ADA", "AVAX", "DOT", "LINK", "TON", "TRX",
            "SHIB", "ARB", "OP", "SUI", "APT", "NEAR", "ATOM", "UNI", "AAVE", "MKR", "LDO");

    private final ThemeRepository themeRepository;
    private final ThemeConstituentRepository constituentRepository;
    private final ThemeMapLoader mapLoader;
    private final InstrumentRepository instrumentRepository;
    private final SupplySnapshotRepository supplyRepository;

    public ThemeService(ThemeRepository themeRepository,
                        ThemeConstituentRepository constituentRepository,
                        ThemeMapLoader mapLoader,
                        InstrumentRepository instrumentRepository,
                        SupplySnapshotRepository supplyRepository) {
        this.themeRepository = themeRepository;
        this.constituentRepository = constituentRepository;
        this.mapLoader = mapLoader;
        this.instrumentRepository = instrumentRepository;
        this.supplyRepository = supplyRepository;
    }

    /** Theme list filtered by market/q and classification flags. */
    @Transactional(readOnly = true)
    public List<ThemeDto.ThemeRow> list(String market, String q, boolean unclassifiedOnly,
                                        boolean lowConfidenceOnly) {
        String normalizedMarket = normalizeMarket(market);
        String query = q == null ? null : q.trim().toLowerCase();

        List<Theme> themes = normalizedMarket == null
                ? themeRepository.findAll()
                : themeRepository.findByMarket(normalizedMarket);

        if (themes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = themes.stream().map(Theme::getId).toList();
        Map<Long, List<ThemeConstituent>> byTheme = new HashMap<>();
        for (ThemeConstituent c : constituentRepository.findByThemeIdIn(ids)) {
            byTheme.computeIfAbsent(c.getThemeId(), k -> new ArrayList<>()).add(c);
        }

        List<ThemeDto.ThemeRow> rows = new ArrayList<>();
        for (Theme t : themes) {
            if (query != null && !query.isEmpty() && !t.getName().toLowerCase().contains(query)) {
                continue;
            }
            List<ThemeConstituent> cs = byTheme.getOrDefault(t.getId(), List.of());
            int unclassified = (int) cs.stream().filter(c -> UNCLASSIFIED.equals(t.getName())).count();
            int lowConf = (int) cs.stream().filter(ThemeService::isLowConfidence).count();
            boolean isUnclassifiedTheme = UNCLASSIFIED.equals(t.getName());
            if (unclassifiedOnly && !isUnclassifiedTheme) {
                continue;
            }
            if (lowConfidenceOnly && lowConf == 0) {
                continue;
            }
            rows.add(new ThemeDto.ThemeRow(t.getId(), t.getMarket(), t.getName(), cs.size(),
                    isUnclassifiedTheme ? cs.size() : unclassified, lowConf));
        }
        rows.sort(Comparator.comparing(ThemeDto.ThemeRow::market)
                .thenComparing(Comparator.comparingInt(ThemeDto.ThemeRow::constituentCount).reversed()));
        return rows;
    }

    /** Constituents of a theme with their classification provenance. */
    @Transactional(readOnly = true)
    public ThemeDto.Constituents constituents(Long themeId) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Theme not found: " + themeId));
        List<ThemeConstituent> cs = constituentRepository.findByThemeId(themeId);
        cs.sort(Comparator.comparing(ThemeConstituent::getInstrumentRef));
        List<ThemeDto.Constituent> items = new ArrayList<>(cs.size());
        for (ThemeConstituent c : cs) {
            items.add(new ThemeDto.Constituent(
                    c.getInstrumentRef(), c.getDisplayName(), c.getClassificationSource(),
                    c.getClassificationConfidence() == null ? null
                            : c.getClassificationConfidence().toPlainString()));
        }
        return new ThemeDto.Constituents(theme.getId(), theme.getMarket(), theme.getName(), items);
    }

    @Transactional(readOnly = true)
    public boolean isSeeded() {
        return themeRepository.count() > 0;
    }

    /**
     * (Re)build the theme model for all markets from the JSON maps + heuristic.
     * Idempotent: clears existing constituents/themes first. Best-effort — each
     * market is built independently.
     */
    @Transactional
    public void seed() {
        Instant now = Instant.now();
        constituentRepository.deleteAllInBatch();
        themeRepository.deleteAllInBatch();

        seedCrypto(now);
        seedEquities(US, "US", now);
        seedEquities(KR, "KOSPI", now);
        seedEquities(KR, "KOSDAQ", now);
    }

    private void seedCrypto(Instant now) {
        Map<String, ThemeMapLoader.Entry> map = mapLoader.load(CRYPTO);
        // Refs: latest supply snapshot symbols if present, else the known seed set.
        Map<String, String> refToName = new LinkedHashMap<>();
        List<SupplySnapshot> supply = supplyRepository.findLatestBatch();
        if (!supply.isEmpty()) {
            for (SupplySnapshot s : supply) {
                if (s.getSymbol() != null) {
                    refToName.putIfAbsent(s.getSymbol().toUpperCase(), s.getName());
                }
            }
        } else {
            for (String sym : SEED_COINS) {
                refToName.put(sym, sym);
            }
        }
        classifyAndPersist(CRYPTO, refToName, map, now, ThemeService::cryptoHeuristic);
    }

    private void seedEquities(String themeMarket, String instrumentMarket, Instant now) {
        Map<String, ThemeMapLoader.Entry> map = mapLoader.load(themeMarket);
        Map<String, String> refToName = new LinkedHashMap<>();
        for (Instrument i : instrumentRepository.findByMarket(instrumentMarket)) {
            String ref = equityRef(themeMarket, i.getSymbol());
            if (ref != null) {
                refToName.putIfAbsent(ref, i.getName());
            }
        }
        if (refToName.isEmpty()) {
            return;
        }
        classifyAndPersist(themeMarket, refToName, map, now,
                themeMarket.equals(US) ? ThemeService::usHeuristic : ThemeService::krHeuristic);
    }

    /** Group refs by theme (map first, heuristic fallback) and persist themes+constituents. */
    private void classifyAndPersist(String market, Map<String, String> refToName,
                                    Map<String, ThemeMapLoader.Entry> map, Instant now,
                                    java.util.function.BiFunction<String, String, String> heuristic) {
        // Resolve or create the theme row, caching by name to dedupe.
        Map<String, Theme> themesByName = new HashMap<>();
        for (Map.Entry<String, String> e : refToName.entrySet()) {
            String ref = e.getKey();
            String displayName = e.getValue();

            String themeName;
            String source;
            BigDecimal confidence;
            ThemeMapLoader.Entry mapped = map.get(ref);
            if (mapped != null) {
                themeName = mapped.theme();
                source = mapped.source() == null ? "manual_json" : mapped.source();
                confidence = mapped.confidence();
            } else {
                String h = heuristic.apply(ref, displayName);
                if (h != null) {
                    themeName = h;
                    source = "heuristic";
                    confidence = HEURISTIC_CONFIDENCE;
                } else {
                    themeName = UNCLASSIFIED;
                    source = "heuristic";
                    confidence = BigDecimal.ZERO;
                }
            }

            // Save-if-absent on (market, name): seedEquities is called twice for KR
            // (KOSPI + KOSDAQ), so the same (market, name) can recur across calls and
            // the in-memory cache alone won't prevent a duplicate-key violation on
            // uq_theme_market_name. Resolve against the DB too.
            Theme theme = themesByName.computeIfAbsent(themeName, n ->
                    themeRepository.findByMarketAndName(market, n)
                            .orElseGet(() -> themeRepository.save(Theme.of(market, n, now))));
            constituentRepository.save(ThemeConstituent.of(
                    theme.getId(), ref, displayName, source, confidence, now));
        }
    }

    // ---- heuristics (lightweight keyword classification; theme_sector_tab fallback) ----

    static String cryptoHeuristic(String symbol, String name) {
        return switch (symbol) {
            case "BTC", "LTC", "BCH" -> "L1/결제";
            case "ETH", "SOL", "ADA", "AVAX", "DOT", "NEAR", "APT", "SUI", "TON", "TRX", "ATOM" -> "L1/스마트컨트랙트";
            case "ARB", "OP" -> "L2";
            case "UNI", "AAVE", "MKR", "LDO" -> "DeFi";
            case "DOGE", "SHIB", "PEPE", "BONK" -> "밈";
            case "LINK" -> "오라클";
            case "XRP" -> "결제";
            default -> null;
        };
    }

    static String usHeuristic(String ticker, String name) {
        String n = name == null ? "" : name.toLowerCase();
        return switch (ticker) {
            case "NVDA", "AMD", "AVGO", "QCOM", "INTC" -> "반도체/AI";
            case "MSFT", "ADBE", "CRM", "INTU", "ORCL" -> "소프트웨어/클라우드";
            case "AAPL", "META", "GOOGL", "GOOG", "AMZN", "NFLX" -> "빅테크/플랫폼";
            case "JPM", "BAC", "V", "MA" -> "금융";
            case "LLY", "JNJ", "MRK", "ABBV", "UNH", "TMO" -> "헬스케어";
            case "XOM", "CVX" -> "에너지";
            case "TSLA" -> "전기차";
            default -> n.contains("bank") ? "금융" : null;
        };
    }

    static String krHeuristic(String code, String name) {
        String n = name == null ? "" : name;
        if (n.contains("반도체") || code.equals("005930") || code.equals("000660")) {
            return "반도체";
        }
        if (n.contains("바이오") || n.contains("셀트리온") || n.contains("제약")) {
            return "바이오/제약";
        }
        if (n.contains("에코프로") || n.contains("엘앤에프") || n.contains("SDI") || n.contains("배터리")) {
            return "2차전지";
        }
        if (n.contains("게임즈") || n.contains("펄어비스") || n.contains("NAVER") || n.contains("카카오")) {
            return "인터넷/게임";
        }
        if (n.contains("차") || n.contains("모비스")) {
            return "자동차";
        }
        return null;
    }

    /** US ref = ticker; KR ref = 6-digit code (strip .KS/.KQ). */
    private static String equityRef(String themeMarket, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        if (KR.equals(themeMarket)) {
            int dot = symbol.indexOf('.');
            return (dot > 0 ? symbol.substring(0, dot) : symbol).toUpperCase();
        }
        return symbol.toUpperCase();
    }

    private static boolean isLowConfidence(ThemeConstituent c) {
        BigDecimal conf = c.getClassificationConfidence();
        return conf != null && conf.compareTo(LOW_CONFIDENCE) < 0;
    }

    private static String normalizeMarket(String market) {
        if (market == null || market.isBlank()) {
            return null;
        }
        String m = market.trim().toUpperCase();
        if (!m.equals(CRYPTO) && !m.equals(US) && !m.equals(KR)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "market must be CRYPTO, US or KR");
        }
        return m;
    }
}
