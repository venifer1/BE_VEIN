package com.vein.signal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 활성 신호의 종합 Pattern Score를 주기적으로/기동 시 재계산한다(R90).
 *
 * <p>성과 스케줄러({@link SignalPerformanceScheduler})와 달리 <b>ingestion 게이트와 무관</b>하게
 * 동작한다({@code vein.signal.pattern-score.enabled}, 기본 on). 계산은 전부 DB 읽기(캔들·지표·
 * 뉴스)라 ingestion 없이도 가볍고, 그래야 홈 top 정렬이 항상 최신 근거로 유지된다. ShedLock으로
 * 다중 노드 중 하나만 실행. 기동 시 1회는 백그라운드 스레드로 돌려 부팅을 막지 않는다.
 */
@Component
@ConditionalOnProperty(name = "vein.signal.pattern-score.enabled", havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class SignalPatternScoreScheduler {

    private final SignalPatternScoreService patternScoreService;

    public SignalPatternScoreScheduler(SignalPatternScoreService patternScoreService) {
        this.patternScoreService = patternScoreService;
    }

    /** 기동 직후 1회 백필(백그라운드 스레드 — 부팅 비차단). */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread t = new Thread(this::run, "pattern-score-startup");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(fixedDelayString = "${vein.signal.pattern-score.refresh-ms:900000}",
            initialDelayString = "${vein.signal.pattern-score.refresh-ms:900000}")
    @SchedulerLock(name = "signal-pattern-score", lockAtMostFor = "PT14M", lockAtLeastFor = "PT10S")
    public void scheduled() {
        run();
    }

    private void run() {
        try {
            patternScoreService.refreshActive();
        } catch (RuntimeException e) {
            log.warn("pattern-score refresh failed", e);
        }
    }
}
