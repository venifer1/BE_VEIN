package com.vein.news;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Polls news providers ~every 15s (breaking_news_tab REFRESH_MS). Gated by
 * {@code vein.ingestion.enabled} so tests/startup stay hermetic; ShedLock
 * prevents duplicate polls across nodes.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class NewsScheduler {

    private final NewsService newsService;

    public NewsScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedDelayString = "${vein.news.refresh-ms:15000}")
    @SchedulerLock(name = "refresh-news", lockAtMostFor = "PT60S", lockAtLeastFor = "PT3S")
    public void refresh() {
        try {
            newsService.refresh();
        } catch (RuntimeException e) {
            log.warn("news refresh failed", e);
        }
    }
}
