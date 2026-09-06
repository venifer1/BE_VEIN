package com.vein.market;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vein.market.index.MarketIndexService;
import com.vein.market.kimchi.KimchiPremiumService;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodic terminal refresh (~60s) for market indices and kimchi premium
 * (legacy terminal auto-refresh). Active only when {@code vein.ingestion.enabled=true}
 * so tests/startup stay hermetic. ShedLock prevents duplicate polls across nodes.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class TerminalScheduler {

    private final MarketIndexService indexService;
    private final KimchiPremiumService kimchiService;

    public TerminalScheduler(MarketIndexService indexService, KimchiPremiumService kimchiService) {
        this.indexService = indexService;
        this.kimchiService = kimchiService;
    }

    @Scheduled(fixedDelayString = "${vein.terminal.refresh-ms:60000}")
    @SchedulerLock(name = "refresh-market-indices", lockAtMostFor = "PT55S", lockAtLeastFor = "PT5S")
    public void refreshIndices() {
        try {
            indexService.refresh();
        } catch (RuntimeException e) {
            log.warn("market-indices refresh failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${vein.terminal.refresh-ms:60000}")
    @SchedulerLock(name = "refresh-kimchi-premium", lockAtMostFor = "PT55S", lockAtLeastFor = "PT5S")
    public void refreshKimchi() {
        try {
            kimchiService.refresh();
        } catch (RuntimeException e) {
            log.warn("kimchi-premium refresh failed", e);
        }
    }
}
