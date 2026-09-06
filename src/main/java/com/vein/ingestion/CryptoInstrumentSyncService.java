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
import com.vein.market.provider.upbit.UpbitMarketDataProvider;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Syncs the CRYPTO instrument master from Upbit's full KRW market list (~200)
 * so search / charts / watchlist / scanner cover the whole universe — not just
 * the ~20 majors seeded in V5. Without this, real coins like KRW-ENA (Ethena)
 * have candles/movers (those read the live ticker directly) but are NOT
 * searchable, because /instruments queries the instruments table.
 *
 * <p>Mirrors {@link EquityInstrumentSyncService}: upsert each Upbit KRW market
 * into {@code instruments} (CRYPTO/UPBIT/symbol/koreanName/KRW/ACTIVE) and the
 * matching {@code provider_symbols} (UPBIT = the KRW-XXX market; BINANCE =
 * &lt;base&gt;USDT for kimchi/derivatives). Idempotent + per-symbol resilient;
 * gated by {@code vein.ingestion.enabled}; runs before the crypto candle poll in
 * {@link StartupIngestionRunner} and daily.
 */
@Service
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class CryptoInstrumentSyncService {

    private static final String MARKET = "CRYPTO";
    private static final String EXCHANGE = "UPBIT";
    private static final String QUOTE = "KRW";
    private static final String ACTIVE = "ACTIVE";

    private final UpbitMarketDataProvider upbit;
    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;

    public CryptoInstrumentSyncService(UpbitMarketDataProvider upbit,
                                       InstrumentRepository instrumentRepository,
                                       ProviderSymbolRepository providerSymbolRepository) {
        this.upbit = upbit;
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
    }

    /** Daily re-sync (03:20 KST) to track Upbit listings/delistings. */
    @Scheduled(cron = "${vein.ingestion.crypto-sync-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "sync-crypto-instruments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void scheduledSync() {
        sync();
    }

    /** Upsert the full Upbit KRW universe. Never throws (per-symbol isolation). */
    @Transactional
    public int sync() {
        List<InstrumentInfo> remote;
        try {
            remote = upbit.listInstruments();
        } catch (RuntimeException e) {
            log.warn("crypto instrument sync: Upbit market list unavailable: {}", e.getMessage());
            return 0;
        }
        int inserted = 0;
        for (InstrumentInfo info : remote) {
            String symbol = info.symbol();
            if (symbol == null || !symbol.startsWith("KRW-")) {
                continue;
            }
            try {
                Instrument inst = instrumentRepository.findFirstByMarketAndSymbol(MARKET, symbol)
                        .orElse(null);
                if (inst == null) {
                    inst = instrumentRepository.save(Instrument.of(
                            MARKET, EXCHANGE, symbol, info.koreanName(), QUOTE, ACTIVE));
                    inserted++;
                } else {
                    // Prefer the Upbit Korean name even for V5-seeded majors (e.g.
                    // KRW-XRP "Ripple" -> "리플") so coins display Korean-name-first.
                    inst.rename(info.koreanName());
                }
                upsertProviderSymbol(inst.getId(), EXCHANGE, symbol);
                String base = symbol.substring("KRW-".length());
                if (!base.isBlank()) {
                    upsertProviderSymbol(inst.getId(), "BINANCE", base + "USDT");
                }
            } catch (RuntimeException e) {
                log.warn("crypto instrument sync: symbol {} failed: {}", symbol, e.getMessage());
            }
        }
        log.info("crypto instrument sync: {} remote KRW markets, {} new instruments", remote.size(), inserted);
        return inserted;
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
