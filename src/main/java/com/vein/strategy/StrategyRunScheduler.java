package com.vein.strategy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Daily re-run of every saved strategy's backtest so performance history
 * accrues (기획서 §12 seed). Kept OFF the hot path — it runs once a day and is a
 * handful of backtests. Gated by {@code vein.ingestion.enabled}; ShedLock so
 * only one node runs the batch. Per-strategy failures are swallowed inside
 * {@link StrategyService#runAll()} so a bad strategy never aborts the run.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class StrategyRunScheduler {

    private final StrategyService strategyService;

    public StrategyRunScheduler(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "strategy-performance-run", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void runDaily() {
        try {
            strategyService.runAll();
        } catch (RuntimeException e) {
            log.warn("daily strategy performance run failed", e);
        }
    }
}
