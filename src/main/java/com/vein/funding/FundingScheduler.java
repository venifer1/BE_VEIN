package com.vein.funding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Refreshes funding-arb snapshots ~every 5min (funding_arb_tab REFRESH_SECONDS).
 * Gated by {@code vein.ingestion.enabled}; ShedLock prevents duplicate polls.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class FundingScheduler {

    private final FundingService fundingService;

    public FundingScheduler(FundingService fundingService) {
        this.fundingService = fundingService;
    }

    @Scheduled(fixedDelayString = "${vein.funding.refresh-ms:300000}")
    @SchedulerLock(name = "refresh-funding", lockAtMostFor = "PT4M", lockAtLeastFor = "PT5S")
    public void refresh() {
        try {
            fundingService.refresh();
        } catch (RuntimeException e) {
            log.warn("funding refresh failed", e);
        }
    }
}
