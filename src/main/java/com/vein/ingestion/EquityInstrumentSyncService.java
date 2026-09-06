package com.vein.ingestion;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.ProviderSymbol;
import com.vein.instrument.ProviderSymbolRepository;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.equity.EquitySidecarClient;
import com.vein.market.provider.equity.KrEquityProvider;
import com.vein.market.provider.equity.UsEquityProvider;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Syncs the instrument master from the Python sidecar so the scanner covers the
 * REAL US/KOSPI/KOSDAQ universe (US ~171, KOSPI ~100, KOSDAQ ~150) instead of the
 * thin V6 Flyway presets (~35 US + 20 KOSPI + 10 KOSDAQ).
 *
 * <p>For each market it calls {@link EquitySidecarClient#listInstruments(String)}
 * ({@code [{symbol,name}]}) and UPSERTS into {@code instruments} + the matching
 * {@code provider_symbols} row:
 * <ul>
 *   <li>match an existing instrument by the {@code (market, symbol)} pair so a symbol
 *       V6 already seeded (e.g. {@code AAPL} on exchange {@code NASDAQ}, or
 *       {@code 005930.KS} on {@code KRX}) is recognised regardless of which board V6
 *       assigned it — this avoids violating the {@code (exchange, symbol)} unique key;</li>
 *   <li>NEW symbols are INSERTED with exchange {@code NASDAQ} for US (the sidecar
 *       returns only {symbol,name} with no board, and most V6 US rows are NASDAQ) and
 *       {@code KRX} for KOSPI/KOSDAQ — the exact V6 convention;</li>
 *   <li>quote_currency = USD (US) / KRW (KR), status = ACTIVE;</li>
 *   <li>provider_symbols: YFINANCE for US, and for KR both YFINANCE (the {@code .KS}/
 *       {@code .KQ} symbol) and PYKRX (the numeric code without suffix), exactly as V6 §9.</li>
 * </ul>
 *
 * <p>Idempotent: existing instruments/provider-symbols are left untouched (no
 * unique-constraint violation, no overwrite of V6 boards). The sidecar is optional
 * infrastructure — ANY failure is caught and logged at WARN and the sync continues;
 * an empty list (sidecar down) is a no-op. Gated by {@code vein.ingestion.enabled};
 * wired to run BEFORE the equity candle backfill in {@link StartupIngestionRunner}
 * and on a daily {@link Scheduled} job.
 */
@Service
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class EquityInstrumentSyncService {

    private static final String ACTIVE = "ACTIVE";
    private static final List<String> MARKETS = List.of("US", "KOSPI", "KOSDAQ");

    private final EquitySidecarClient sidecar;
    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;

    public EquityInstrumentSyncService(EquitySidecarClient sidecar,
                                       InstrumentRepository instrumentRepository,
                                       ProviderSymbolRepository providerSymbolRepository) {
        this.sidecar = sidecar;
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
    }

    /** Daily re-sync (03:30 KST) so the universe tracks listings/delistings. */
    @Scheduled(cron = "${vein.ingestion.instrument-sync-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "sync-equity-instruments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void scheduledSync() {
        syncAll();
    }

    /** Sync all equity markets. Never throws (per-market + per-symbol isolation). */
    public void syncAll() {
        int total = 0;
        for (String market : MARKETS) {
            try {
                total += syncMarket(market);
            } catch (RuntimeException e) {
                log.warn("instrument sync: market {} failed: {}", market, e.getMessage());
            }
        }
        log.info("instrument sync: {} new instruments inserted across {}", total, MARKETS);
    }

    /** Upsert one market's universe from the sidecar. Returns # of NEW instruments. */
    @Transactional
    public int syncMarket(String market) {
        List<InstrumentInfo> remote = sidecar.listInstruments(market);
        if (remote.isEmpty()) {
            log.warn("instrument sync: market {} returned no instruments (sidecar down?) — skipping", market);
            return 0;
        }
        boolean kr = !"US".equals(market);
        String exchange = kr ? "KRX" : "NASDAQ";
        String quoteCurrency = kr ? "KRW" : "USD";
        int inserted = 0;
        for (InstrumentInfo info : remote) {
            String symbol = info.symbol();
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            try {
                Instrument inst = instrumentRepository.findFirstByMarketAndSymbol(market, symbol)
                        .orElse(null);
                if (inst == null) {
                    inst = instrumentRepository.save(Instrument.of(
                            market, exchange, symbol, info.koreanName(), quoteCurrency, ACTIVE));
                    inserted++;
                }
                // provider_symbols upsert (V6 §9 convention; idempotent on (provider, provider_symbol)).
                // YFINANCE symbol == the sidecar ticker (AAPL / 005930.KS / 247540.KQ).
                upsertProviderSymbol(inst.getId(), UsEquityProvider.CODE, symbol);
                if (kr) {
                    // PYKRX symbol == the numeric code without the .KS/.KQ suffix.
                    upsertProviderSymbol(inst.getId(), KrEquityProvider.CODE, stripSuffix(symbol));
                }
            } catch (RuntimeException e) {
                log.warn("instrument sync: market {} symbol {} failed: {}", market, symbol, e.getMessage());
            }
        }
        log.info("instrument sync: market {} — {} remote, {} new instruments", market, remote.size(), inserted);
        return inserted;
    }

    /** {@code 005930.KS} -> {@code 005930} (pykrx code); no-op if there is no dot. */
    private static String stripSuffix(String symbol) {
        int dot = symbol.indexOf('.');
        return dot < 0 ? symbol : symbol.substring(0, dot);
    }

    private void upsertProviderSymbol(Long instrumentId, String provider, String providerSymbol) {
        if (instrumentId == null || providerSymbol == null || providerSymbol.isBlank()) {
            return;
        }
        if (providerSymbolRepository.existsByProviderAndProviderSymbol(provider, providerSymbol)) {
            return;
        }
        providerSymbolRepository.save(ProviderSymbol.of(instrumentId, provider, providerSymbol));
    }
}
