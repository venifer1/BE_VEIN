package com.vein.signal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodically computes signal performance (기획서 §31) for horizons that have
 * now elapsed but not yet been measured. Kept OFF the hot scan path: it runs on
 * its own ~10 min cadence. Gated by {@code vein.ingestion.enabled}; ShedLock so
 * only one node evaluates at a time.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class SignalPerformanceScheduler {

    private final SignalPerformanceService performanceService;

    public SignalPerformanceScheduler(SignalPerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @Scheduled(fixedDelayString = "${vein.signal.performance.refresh-ms:600000}")
    @SchedulerLock(name = "signal-performance", lockAtMostFor = "PT9M", lockAtLeastFor = "PT10S")
    public void evaluate() {
        try {
            performanceService.evaluateDue();
        } catch (RuntimeException e) {
            log.warn("signal performance evaluation failed", e);
        }
    }
}
