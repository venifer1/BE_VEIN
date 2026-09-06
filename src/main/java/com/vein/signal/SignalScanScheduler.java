package com.vein.signal;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vein.common.Timeframe;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Multi-market, multi-timeframe pattern scan (부록 E / API_CONTRACT v2 §4). Runs
 * the pattern detectors (ABC/TOP/IMALOL) per (instrument, market, timeframe)
 * via {@link SignalDetectionService}. Active only when
 * {@code vein.ingestion.enabled=true}. Each method is ShedLock-guarded so only one
 * node scans a given timeframe at a time.
 *
 * <p>Legacy re-scan cadence:
 * <ul>
 *   <li>주(1w) / 3일(3d) / 일(1d): every 4h,</li>
 *   <li>4h / 1h / 15m: each on its own period (4h / 1h / 15m).</li>
 * </ul>
 * Equity markets (US/KOSPI/KOSDAQ) support only 1d/3d/1w; their candles are
 * stubbed/empty in Phase 1 so the scan simply finds nothing there.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
public class SignalScanScheduler {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final List<String> ALL_MARKETS = List.of("CRYPTO", "US", "KOSPI", "KOSDAQ");

    private final SignalDetectionService detectionService;
    private final SignalStatusTransitionService transitionService;
    private final InstrumentRepository instrumentRepository;

    public SignalScanScheduler(SignalDetectionService detectionService,
                               SignalStatusTransitionService transitionService,
                               InstrumentRepository instrumentRepository) {
        this.detectionService = detectionService;
        this.transitionService = transitionService;
        this.instrumentRepository = instrumentRepository;
    }

    /** 15m crypto scan, every 15 minutes. */
    @Scheduled(cron = "0 2/15 * * * *")
    @SchedulerLock(name = "scan-15m", lockAtMostFor = "PT12M", lockAtLeastFor = "PT10S")
    public void scan15m() {
        scan(Timeframe.M15);
    }

    /** 1h crypto scan, five minutes past the hour. */
    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(name = "scan-1h", lockAtMostFor = "PT20M", lockAtLeastFor = "PT10S")
    public void scanHourly() {
        scan(Timeframe.H1);
    }

    /** 4h crypto scan, six minutes past every 4th hour. */
    @Scheduled(cron = "0 6 0/4 * * *")
    @SchedulerLock(name = "scan-4h", lockAtMostFor = "PT20M", lockAtLeastFor = "PT10S")
    public void scan4h() {
        scan(Timeframe.H4);
    }

    /**
     * 주(1w) / 3일(3d) / 일(1d) scan across all markets, every 4 hours
     * (eight minutes past every 4th hour), per the legacy long-TF cadence.
     */
    @Scheduled(cron = "0 8 0/4 * * *")
    @SchedulerLock(name = "scan-multiday", lockAtMostFor = "PT45M", lockAtLeastFor = "PT10S")
    public void scanMultiDay() {
        scan(Timeframe.D1);
        scan(Timeframe.D3);
        scan(Timeframe.W1);
    }

    /**
     * Scan all ACTIVE instruments across all markets for the timeframe, skipping
     * (market, timeframe) combos the market does not support. Re-evaluates active
     * signal statuses afterwards.
     */
    private void scan(Timeframe tf) {
        for (String market : ALL_MARKETS) {
            if (!tf.isSupportedFor(market)) {
                continue;
            }
            List<Instrument> instruments =
                    instrumentRepository.findByMarketAndStatus(market, STATUS_ACTIVE);
            for (Instrument instrument : instruments) {
                detectionService.scanInstrument(instrument.getId(), tf);
            }
        }
        transitionService.reevaluateActive(Instant.now());
    }
}
