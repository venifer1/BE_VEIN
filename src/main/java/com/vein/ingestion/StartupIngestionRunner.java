package com.vein.ingestion;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.vein.common.Timeframe;
import com.vein.funding.FundingService;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.index.MarketIndexService;
import com.vein.market.kimchi.KimchiPremiumService;
import com.vein.news.NewsService;
import com.vein.signal.SignalDetectionService;
import com.vein.signal.SignalPerformanceService;
import com.vein.signal.SignalStatusTransitionService;
import com.vein.supply.SupplyService;
import com.vein.theme.ThemeService;
import com.vein.tvl.TvlService;

import lombok.extern.slf4j.Slf4j;

/**
 * One-off startup backfill + scan so the terminal has candles/signals/feeds
 * immediately on boot instead of waiting for the next cron boundary
 * ({@link IngestionScheduler} / {@link com.vein.signal.SignalScanScheduler} are
 * cron-only). Active only when {@code vein.ingestion.enabled=true}.
 *
 * <p>All work runs on a daemon background thread so application startup is never
 * blocked. Every step is wrapped in try/catch + log so one failing feed (or one
 * delisted symbol such as {@code KRW-MATIC}) can't abort the whole backfill. The
 * candle upsert is by primary key, so re-running is idempotent.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class StartupIngestionRunner implements ApplicationRunner {

    private static final String ACTIVE = "ACTIVE";
    /** Crypto timeframes to backfill on boot. Keep in sync with crypto scanner coverage. */
    private static final List<Timeframe> CRYPTO_TIMEFRAMES =
            List.of(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.D3, Timeframe.W1);
    /** Equity markets and the timeframes they support (1d/3d/1w). */
    private static final List<String> EQUITY_MARKETS = List.of("US", "KOSPI", "KOSDAQ");
    private static final List<Timeframe> EQUITY_TIMEFRAMES =
            List.of(Timeframe.D1, Timeframe.D3, Timeframe.W1);

    private final IngestionService ingestionService;
    private final CryptoInstrumentSyncService cryptoInstrumentSyncService;
    private final EquityInstrumentSyncService equityInstrumentSyncService;
    private final EquityIngestionService equityIngestionService;
    private final SignalDetectionService detectionService;
    private final SignalStatusTransitionService transitionService;
    private final SignalPerformanceService performanceService;
    private final InstrumentRepository instrumentRepository;
    private final MarketIndexService indexService;
    private final KimchiPremiumService kimchiService;
    private final FundingService fundingService;
    private final TvlService tvlService;
    private final SupplyService supplyService;
    private final NewsService newsService;
    private final ThemeService themeService;
    private final int initialBackfillCount;

    public StartupIngestionRunner(IngestionService ingestionService,
                                  CryptoInstrumentSyncService cryptoInstrumentSyncService,
                                  EquityInstrumentSyncService equityInstrumentSyncService,
                                  EquityIngestionService equityIngestionService,
                                  SignalDetectionService detectionService,
                                  SignalStatusTransitionService transitionService,
                                  SignalPerformanceService performanceService,
                                  InstrumentRepository instrumentRepository,
                                  MarketIndexService indexService,
                                  KimchiPremiumService kimchiService,
                                  FundingService fundingService,
                                  TvlService tvlService,
                                  SupplyService supplyService,
                                  NewsService newsService,
                                  ThemeService themeService,
                                  @Value("${vein.ingestion.initial-backfill-count:1000}")
                                  int initialBackfillCount) {
        this.ingestionService = ingestionService;
        this.cryptoInstrumentSyncService = cryptoInstrumentSyncService;
        this.equityInstrumentSyncService = equityInstrumentSyncService;
        this.equityIngestionService = equityIngestionService;
        this.detectionService = detectionService;
        this.transitionService = transitionService;
        this.performanceService = performanceService;
        this.instrumentRepository = instrumentRepository;
        this.indexService = indexService;
        this.kimchiService = kimchiService;
        this.fundingService = fundingService;
        this.tvlService = tvlService;
        this.supplyService = supplyService;
        this.newsService = newsService;
        this.themeService = themeService;
        this.initialBackfillCount = Math.max(initialBackfillCount, 200);
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::backfill, "startup-ingestion");
        t.setDaemon(true);
        t.start();
    }

    /** Runs off the startup thread; never throws (each step is isolated). */
    void backfill() {
        log.info("startup backfill: starting (non-blocking)");

        // a0. Sync the FULL Upbit KRW universe (~200) into instruments so every coin
        //     (e.g. KRW-ENA / Ethena) is searchable — not just the V5 majors. Before
        //     the poll below so candle ingestion covers the freshly-synced universe.
        try {
            cryptoInstrumentSyncService.sync();
        } catch (RuntimeException e) {
            log.warn("startup backfill: crypto instrument sync failed: {}", e.getMessage());
        }

        // a. Populate candles per crypto timeframe. poll() iterates active instruments
        //    and is itself per-instrument resilient.
        int candles = 0;
        for (Timeframe tf : CRYPTO_TIMEFRAMES) {
            try {
                ingestionService.poll(tf);
                candles++;
            } catch (RuntimeException e) {
                log.warn("startup backfill: poll {} failed: {}", tf.code(), e.getMessage());
            }
        }

        // b. One-off scan across active crypto instruments × the crypto timeframes.
        List<Instrument> cryptos = instrumentRepository.findByMarketAndStatus("CRYPTO", ACTIVE);

        int cryptoBackfills = 0;
        for (Timeframe tf : CRYPTO_TIMEFRAMES) {
            for (Instrument inst : cryptos) {
                try {
                    ingestionService.backfill(inst.getId(), tf, initialBackfillCount);
                    cryptoBackfills++;
                } catch (RuntimeException e) {
                    log.warn("startup backfill: instrument {} tf {} failed: {}",
                            inst.getId(), tf.code(), e.getMessage());
                }
            }
        }

        int signals = 0;
        for (Timeframe tf : CRYPTO_TIMEFRAMES) {
            for (Instrument inst : cryptos) {
                try {
                    signals += detectionService.scanInstrument(inst.getId(), tf);
                } catch (RuntimeException e) {
                    log.warn("startup scan: instrument {} tf {} failed: {}",
                            inst.getId(), tf.code(), e.getMessage());
                }
            }
        }

        // b1b. Re-evaluate status NOW (crypto + any equity candles already persisted
        //      from prior runs) so DETECTED→NEAR_COMPLETION/EXPIRED/INVALIDATED are
        //      live within seconds of boot, before the slow equity backfill below.
        try {
            transitionService.reevaluateActive(java.time.Instant.now());
        } catch (RuntimeException e) {
            log.warn("startup backfill: early status reevaluation failed: {}", e.getMessage());
        }

        // a1. Sync the equity instrument master from the sidecar so the equity candle
        //     backfill covers the REAL US/KOSPI/KOSDAQ universe. Idempotent + resilient.
        try {
            equityInstrumentSyncService.syncAll();
        } catch (RuntimeException e) {
            log.warn("startup backfill: equity instrument sync failed: {}", e.getMessage());
        }
        // a2. Populate equity candles (US/KOSPI/KOSDAQ × 1d/3d/1w) via the equity
        //     providers (YFINANCE/PYKRX → sidecar, synthetic fallback). Slow (yfinance),
        //     so it runs AFTER crypto scan/transition to keep boot responsive.
        try {
            equityIngestionService.updateAll();
        } catch (RuntimeException e) {
            log.warn("startup backfill: equity update failed: {}", e.getMessage());
        }

        // b2. One-off scan across active equity instruments × equity timeframes so
        //     real US/KOSPI/KOSDAQ signals appear on boot too (the cron scan already
        //     covers all markets; this is the immediate-on-startup equivalent).
        int equitySignals = 0;
        int equityInstruments = 0;
        for (String market : EQUITY_MARKETS) {
            List<Instrument> equities = instrumentRepository.findByMarketAndStatus(market, ACTIVE);
            equityInstruments += equities.size();
            for (Timeframe tf : EQUITY_TIMEFRAMES) {
                for (Instrument inst : equities) {
                    try {
                        equitySignals += detectionService.scanInstrument(inst.getId(), tf);
                    } catch (RuntimeException e) {
                        log.warn("startup scan (equity): instrument {} tf {} failed: {}",
                                inst.getId(), tf.code(), e.getMessage());
                    }
                }
            }
        }
        log.info("startup backfill: {} crypto timeframes polled, {} crypto history jobs, "
                        + "{} crypto signals across {} crypto instruments; "
                        + "{} equity signals across {} equity instruments",
                candles, cryptoBackfills, signals, cryptos.size(), equitySignals, equityInstruments);

        // b2b. Re-evaluate signal status on boot (EXPIRED TTL, INVALIDATED low-break,
        //      DETECTED→NEAR_COMPLETION when latest close nears C target). The cron
        //      scan does this each cycle; do it on boot too for immediate accuracy.
        try {
            transitionService.reevaluateActive(java.time.Instant.now());
        } catch (RuntimeException e) {
            log.warn("startup backfill: status reevaluation failed: {}", e.getMessage());
        }

        // b3. Compute signal performance (기획서 §31) once on boot so older signals
        //     whose horizons have already elapsed have immediate data. Off the hot
        //     scan path; the scheduler refreshes it on its own cadence afterwards.
        try {
            int perf = performanceService.evaluateDue();
            log.info("startup backfill: signal performance computed {} horizon rows", perf);
        } catch (RuntimeException e) {
            log.warn("startup backfill: signal performance failed: {}", e.getMessage());
        }

        // c. One-off refresh of the other live feeds so the UI has data immediately.
        refresh("market-indices", indexService::refresh);
        refresh("kimchi", kimchiService::refresh);
        refresh("funding", fundingService::refresh);
        refresh("tvl", tvlService::refresh);
        refresh("supply", supplyService::refresh);
        refresh("news", () -> newsService.refresh());
        refresh("theme", themeService::seed);

        log.info("startup backfill: done at {}", Instant.now());
    }

    private void refresh(String name, Runnable action) {
        try {
            action.run();
            log.info("startup refresh: {} ok", name);
        } catch (RuntimeException e) {
            log.warn("startup refresh: {} failed: {}", name, e.getMessage());
        }
    }
}
