package com.vein.theme;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodically re-seeds the theme model (~every 5 min, theme_sector_tab cache TTL).
 * Picks up newly ingested supply snapshots / instruments. Gated by
 * {@code vein.ingestion.enabled}; ShedLock prevents duplicate runs.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class ThemeScheduler {

    private final ThemeService themeService;

    public ThemeScheduler(ThemeService themeService) {
        this.themeService = themeService;
    }

    @Scheduled(fixedDelayString = "${vein.theme.refresh-ms:300000}")
    @SchedulerLock(name = "refresh-themes", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refresh() {
        try {
            themeService.seed();
        } catch (RuntimeException e) {
            log.warn("theme refresh failed", e);
        }
    }
}
