package com.vein.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.notification.Notification;
import com.vein.notification.NotificationPrefService;
import com.vein.notification.NotificationService;
import com.vein.signal.PatternSignal;
import com.vein.signal.PatternSignalRepository;

/**
 * Evaluates enabled alerts against newly created signals and fans out in-app
 * notifications, honouring per-alert cooldown. Dedup is enforced by the
 * notifications unique key; cooldown is advanced only when a notification is
 * actually created.
 */
@Service
@Transactional
public class AlertEvaluationService {

    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final InstrumentRepository instrumentRepository;
    private final PatternSignalRepository patternSignalRepository;
    private final NotificationPrefService notificationPrefService;

    public AlertEvaluationService(AlertRepository alertRepository,
                                  NotificationService notificationService,
                                  InstrumentRepository instrumentRepository,
                                  PatternSignalRepository patternSignalRepository,
                                  NotificationPrefService notificationPrefService) {
        this.alertRepository = alertRepository;
        this.notificationService = notificationService;
        this.instrumentRepository = instrumentRepository;
        this.patternSignalRepository = patternSignalRepository;
        this.notificationPrefService = notificationPrefService;
    }

    /** Main entrypoint: invoked when a new signal is detected. */
    public void onSignalCreated(PatternSignal signal) {
        if (signal == null) {
            return;
        }
        Instant now = Instant.now();
        List<Alert> alerts = alertRepository.findByInstrumentIdAndSignalTypeAndEnabledTrue(
                signal.getInstrumentId(), signal.getType());
        if (alerts.isEmpty()) {
            return;
        }

        String symbol = instrumentRepository.findById(signal.getInstrumentId())
                .map(Instrument::getSymbol)
                .orElse("#" + signal.getInstrumentId());
        String title = symbol + " " + signal.getType() + " signal";
        String body = symbol + " " + signal.getType() + " pattern detected on " + signal.getTimeframe();

        for (Alert alert : alerts) {
            if (alert.inCooldown(now)) {
                continue;
            }
            // 스풀링(R46): 조용한 시간(R42)에는 알림을 드롭하지 않고 창 종료 시각까지 보류한다.
            // 보류 알림은 읽기 모델에서 제외돼 핑/배지가 뜨지 않고, 창이 끝나면 자연히 노출된다
            // (R42의 드롭+후속 재발화와 달리 놓치지 않는다). 보류도 실제 알림이므로 쿨다운은 진전.
            Instant heldUntil = notificationPrefService.quietWindowEnd(alert.getUserId(), now);
            Notification created = notificationService.createInApp(
                    alert.getUserId(), alert.getId(), signal.getId(), title, body, heldUntil);
            if (created != null) {
                alert.setLastTriggeredAt(now);
                alertRepository.save(alert);
            }
        }
    }

    /**
     * Re-evaluate signals detected since the given instant. Optional batch helper;
     * not wired to any scheduler to avoid cross-package coupling.
     */
    public void evaluateRecent(Instant since) {
        if (since == null) {
            return;
        }
        patternSignalRepository.findAll().stream()
                .filter(s -> s.getDetectedAt() != null && !s.getDetectedAt().isBefore(since))
                .forEach(this::onSignalCreated);
    }
}
