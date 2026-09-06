package com.vein.tvl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Refreshes TVL snapshots ~every 60s (defillama_service REFRESH). Gated by
 * {@code vein.ingestion.enabled}; ShedLock prevents duplicate polls.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class TvlScheduler {

    private final TvlService tvlService;

    public TvlScheduler(TvlService tvlService) {
        this.tvlService = tvlService;
    }

    @Scheduled(fixedDelayString = "${vein.tvl.refresh-ms:60000}")
    @SchedulerLock(name = "refresh-tvl", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refresh() {
        try {
            tvlService.refresh();
        } catch (RuntimeException e) {
            log.warn("TVL refresh failed", e);
        }
    }
}
