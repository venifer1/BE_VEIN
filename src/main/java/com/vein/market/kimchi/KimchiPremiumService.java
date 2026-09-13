package com.vein.market.kimchi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.ProviderSymbol;
import com.vein.instrument.ProviderSymbolRepository;
import com.vein.market.provider.binance.BinanceMarketDataProvider;
import com.vein.market.provider.fx.ExchangeRateProvider;
import com.vein.market.provider.upbit.UpbitMarketDataProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Kimchi premium (GET /market/kimchi-premium): Upbit KRW spot vs Binance USDT
 * x USD/KRW. premium_pct = (upbit / (binance * usdkrw) - 1) * 100 (terminal_tab.py).
 * BTC/ETH/XRP are pinned to the top; remaining rows honor {@code ?sort}.
 */
@Service
@Slf4j
public class KimchiPremiumService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final List<String> PINNED = List.of("KRW-BTC", "KRW-ETH", "KRW-XRP");
    private static final String BINANCE = "BINANCE";

    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;
    private final KimchiPremiumRepository repository;
    private final UpbitMarketDataProvider upbit;
    private final BinanceMarketDataProvider binance;
    private final ExchangeRateProvider fx;

    public KimchiPremiumService(InstrumentRepository instrumentRepository,
                                ProviderSymbolRepository providerSymbolRepository,
                                KimchiPremiumRepository repository,
                                UpbitMarketDataProvider upbit,
                                BinanceMarketDataProvider binance,
                                ExchangeRateProvider fx) {
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
        this.repository = repository;
        this.upbit = upbit;
        this.binance = binance;
        this.fx = fx;
    }

    public record Row(Long instrumentId, String symbol, String name,
                      String upbitPrice, String binancePrice, String usdkrw, String premiumPct) {
    }

    /** Latest cached batch, ordered with BTC/ETH/XRP pinned then by {@code sort}. */
    @Transactional(readOnly = true)
    public List<Row> latest(String sort) {
        List<KimchiPremium> batch = repository.findLatestBatch();
        Map<Long, Instrument> byId = new HashMap<>();
        for (Instrument i : instrumentRepository.findByMarket("CRYPTO")) {
            byId.put(i.getId(), i);
        }
        List<Row> rows = new ArrayList<>(batch.size());
        for (KimchiPremium k : batch) {
            Instrument inst = byId.get(k.getInstrumentId());
            rows.add(new Row(
                    k.getInstrumentId(),
                    inst == null ? null : inst.getSymbol(),
                    inst == null ? null : inst.getName(),
                    k.getUpbitPrice().toPlainString(),
                    k.getBinancePrice().toPlainString(),
                    k.getUsdkrw().toPlainString(),
                    k.getPremiumPct().toPlainString()));
        }
        return order(rows, sort);
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc()
                .map(KimchiPremium::getCollectedAt)
                .orElse(null);
    }

    private List<Row> order(List<Row> rows, String sort) {
        Comparator<Row> base;
        String key = sort == null ? "" : sort.trim().toLowerCase();
        base = switch (key) {
            case "premium_asc" -> Comparator.comparing(r -> num(r.premiumPct()));
            // default + premium_desc: highest premium first.
            default -> Comparator.<Row>comparingDouble(r -> num(r.premiumPct())).reversed();
        };
        // Pinned coins always on top, in PINNED order; rest by comparator.
        rows.sort(Comparator
                .comparingInt((Row r) -> {
                    int idx = PINNED.indexOf(r.symbol());
                    return idx < 0 ? PINNED.size() : idx;
                })
                .thenComparing(base));
        return rows;
    }

    private static double num(String s) {
        try {
            return s == null ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Recompute and persist a new kimchi-premium batch. Fault-tolerant: if FX or
     * a provider call fails the run is skipped (previous cache stays valid).
     */
    @Transactional
    public void refresh() {
        BigDecimal usdkrw;
        try {
            usdkrw = fx.fetchUsdKrw();
        } catch (RuntimeException e) {
            log.warn("kimchi refresh skipped — USD/KRW unavailable: {}", e.getMessage());
            return;
        }
        if (usdkrw == null || usdkrw.signum() <= 0) {
            return;
        }

        // Map CRYPTO instruments to their Binance USDT symbol.
        List<Instrument> instruments = instrumentRepository.findByMarketAndStatus("CRYPTO", "ACTIVE");
        Map<Long, String> binanceSymbolByInstrument = new HashMap<>();
        for (ProviderSymbol ps : providerSymbolRepository.findByProvider(BINANCE)) {
            binanceSymbolByInstrument.put(ps.getInstrumentId(), ps.getProviderSymbol());
        }

        List<String> upbitMarkets = instruments.stream().map(Instrument::getSymbol).toList();
        List<String> binanceSymbols = new ArrayList<>(binanceSymbolByInstrument.values());

        Map<String, BigDecimal> upbitPrices;
        Map<String, BigDecimal> binancePrices;
        try {
            upbitPrices = upbit.fetchTickerPrices(upbitMarkets);
            binancePrices = binance.fetchPrices(binanceSymbols);
        } catch (RuntimeException e) {
            log.warn("kimchi refresh skipped — price fetch failed: {}", e.getMessage());
            return;
        }

        Instant now = Instant.now();
        int saved = 0;
        for (Instrument inst : instruments) {
            String binanceSymbol = binanceSymbolByInstrument.get(inst.getId());
            if (binanceSymbol == null) {
                continue;
            }
            BigDecimal upbitPrice = upbitPrices.get(inst.getSymbol());
            BigDecimal binancePrice = binancePrices.get(binanceSymbol.toUpperCase());
            BigDecimal premiumPct = premiumPct(upbitPrice, binancePrice, usdkrw);
            if (premiumPct == null) {
                continue;
            }
            repository.save(KimchiPremium.of(inst.getId(), upbitPrice, binancePrice,
                    usdkrw.setScale(6, RoundingMode.HALF_UP), premiumPct, now));
            saved++;
        }
        log.debug("kimchi refresh saved {} rows", saved);
    }

    /**
     * 김치 프리미엄 = (업비트가 / (바이낸스가 × 환율) − 1) × 100, 소수 4자리 HALF_UP.
     * 입력이 null이거나 바이낸스가/환산 원화가 0 이하이면 계산 불가 → null. 순수 함수.
     */
    static BigDecimal premiumPct(BigDecimal upbitPrice, BigDecimal binancePrice, BigDecimal usdkrw) {
        if (upbitPrice == null || binancePrice == null || usdkrw == null || binancePrice.signum() <= 0) {
            return null;
        }
        BigDecimal baseKrw = binancePrice.multiply(usdkrw, MC);
        if (baseKrw.signum() <= 0) {
            return null;
        }
        return upbitPrice.divide(baseKrw, MC)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
