package com.vein.supply;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Refreshes coin supply snapshots ~every 60s (supply_tab REFRESH_SECONDS). Gated
 * by {@code vein.ingestion.enabled}; ShedLock prevents duplicate polls. The
 * service self-throttles on CoinGecko 429 (Retry-After backoff).
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class SupplyScheduler {

    private final SupplyService supplyService;

    public SupplyScheduler(SupplyService supplyService) {
        this.supplyService = supplyService;
    }

    @Scheduled(fixedDelayString = "${vein.supply.refresh-ms:60000}")
    @SchedulerLock(name = "refresh-supply", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refresh() {
        try {
            supplyService.refresh();
        } catch (RuntimeException e) {
            log.warn("supply refresh failed", e);
        }
    }
}
