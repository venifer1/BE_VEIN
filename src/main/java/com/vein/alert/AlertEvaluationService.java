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
            // 조용한 시간(R42): 이 창에는 새 알림을 만들지 않는다. 쿨다운도 진전시키지 않아
            // 창이 끝난 뒤 후속 신호가 정상적으로 알림을 낸다.
            if (notificationPrefService.isQuietNow(alert.getUserId(), now)) {
                continue;
            }
            Notification created = notificationService.createInApp(
                    alert.getUserId(), alert.getId(), signal.getId(), title, body);
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
