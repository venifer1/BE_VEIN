package com.vein.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vein.common.Timeframe;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodic candle polling per timeframe (부록 D). Active only when
 * {@code vein.ingestion.enabled=true} (off in tests). Each method is guarded by
 * ShedLock so only one node polls a given timeframe at a time.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
public class IngestionScheduler {

    private final IngestionService ingestionService;
    private final EquityIngestionService equityIngestionService;

    public IngestionScheduler(IngestionService ingestionService,
                              EquityIngestionService equityIngestionService) {
        this.ingestionService = ingestionService;
        this.equityIngestionService = equityIngestionService;
    }

    /** 15m crypto candles, shortly before the 15m signal scan. */
    @Scheduled(cron = "0 0/15 * * * *")
    @SchedulerLock(name = "poll-15m", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void poll15m() {
        ingestionService.poll(Timeframe.M15);
    }

    /** Hourly, one minute past the hour. */
    @Scheduled(cron = "0 1 * * * *")
    @SchedulerLock(name = "poll-1h", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void pollHourly() {
        ingestionService.poll(Timeframe.H1);
    }

    /** Every 4 hours, two minutes past the hour. */
    @Scheduled(cron = "0 2 0/4 * * *")
    @SchedulerLock(name = "poll-4h", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void poll4h() {
        ingestionService.poll(Timeframe.H4);
    }

    /** Daily at 00:05 UTC. */
    @Scheduled(cron = "0 5 0 * * *")
    @SchedulerLock(name = "poll-1d", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
    public void pollDaily() {
        ingestionService.poll(Timeframe.D1);
    }

    /** Daily synthetic 3d crypto candles, after the daily source candles refresh. */
    @Scheduled(cron = "0 6 0 * * *")
    @SchedulerLock(name = "poll-3d", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
    public void poll3d() {
        ingestionService.poll(Timeframe.D3);
    }

    /** Daily weekly crypto candle refresh. */
    @Scheduled(cron = "0 7 0 * * *")
    @SchedulerLock(name = "poll-1w", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
    public void pollWeekly() {
        ingestionService.poll(Timeframe.W1);
    }

    /**
     * Daily equity (US/KOSPI/KOSDAQ) candle update at 00:10 UTC, after the crypto
     * daily poll. Equities settle daily, so a once-a-day sweep of 1d/3d/1w is
     * sufficient. Delegates to the equity providers (YFINANCE/PYKRX → Python
     * sidecar with synthetic fallback); never touches Upbit.
     */
    @Scheduled(cron = "0 10 0 * * *")
    @SchedulerLock(name = "poll-equity", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void updateEquities() {
        equityIngestionService.updateAll();
    }
}
