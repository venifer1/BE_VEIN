package com.vein.scalp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Collects + scores scalp microstructure ~every 1s (scalp_metrics ui_refresh_sec).
 * This is heavy REST polling, so it is gated by its own {@code vein.scalp.enabled}
 * flag (default OFF) in addition to the global ingestion gate. ShedLock prevents
 * duplicate collection across nodes.
 */
@Component
@ConditionalOnProperty(name = "vein.scalp.enabled", havingValue = "true")
@Slf4j
public class ScalpScheduler {

    private final ScalpService scalpService;

    public ScalpScheduler(ScalpService scalpService) {
        this.scalpService = scalpService;
    }

    @Scheduled(fixedDelayString = "${vein.scalp.refresh-ms:1000}")
    @SchedulerLock(name = "refresh-scalp", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void refresh() {
        try {
            scalpService.refresh();
        } catch (RuntimeException e) {
            log.warn("scalp refresh failed", e);
        }
    }
}
