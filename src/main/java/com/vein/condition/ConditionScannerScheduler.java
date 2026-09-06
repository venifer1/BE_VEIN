package com.vein.condition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class ConditionScannerScheduler {

    private final ConditionScannerService service;
    private final boolean enabled;

    public ConditionScannerScheduler(ConditionScannerService service,
                                     @Value("${vein.scanner.enabled:${vein.ingestion.enabled:false}}")
                                     boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${vein.scanner.refresh-ms:600000}")
    @SchedulerLock(name = "scanner-rule-alerts", lockAtMostFor = "PT9M", lockAtLeastFor = "PT10S")
    public void evaluateSavedRules() {
        if (!enabled) {
            return;
        }
        service.evaluateEnabledRules();
    }
}
