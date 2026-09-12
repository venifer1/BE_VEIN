package com.vein.alert;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.billing.EntitlementsService;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.instrument.InstrumentService;
import com.vein.notification.NotificationRepository;
import com.vein.signal.SignalType;

/**
 * Alert lifecycle: create / update with ownership and cooldown validation.
 */
@Service
@Transactional
public class AlertService {

    private static final int DEFAULT_COOLDOWN_SEC = 3600;

    private final AlertRepository alertRepository;
    private final InstrumentService instrumentService;
    private final EntitlementsService entitlementsService;
    private final NotificationRepository notificationRepository;

    public AlertService(AlertRepository alertRepository, InstrumentService instrumentService,
                        EntitlementsService entitlementsService,
                        NotificationRepository notificationRepository) {
        this.alertRepository = alertRepository;
        this.instrumentService = instrumentService;
        this.entitlementsService = entitlementsService;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> list(Long userId) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(a -> AlertDto.from(a, symbolOf(a.getInstrumentId())))
                .toList();
    }

    public AlertDto create(Long userId, Long instrumentId, String signalType,
                           String timeframe, String market, Integer cooldownSec) {
        int cooldown = (cooldownSec == null) ? DEFAULT_COOLDOWN_SEC : cooldownSec;
        if (cooldown <= 0) {
            throw new ApiException(ErrorCode.INVALID_COOLDOWN);
        }
        // Validates instrument existence (throws NOT_FOUND).
        var instrument = instrumentService.getById(instrumentId);

        SignalType type = parseSignalType(signalType);

        alertRepository.findByUserIdAndInstrumentIdAndSignalType(userId, instrumentId, type)
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE_ALERT);
                });

        // 구독 게이트(R56, Track C): FREE는 알림 규칙 개수 제한. PRO(-1)는 무제한.
        int limit = entitlementsService.alertLimit(userId);
        if (limit >= 0 && alertRepository.countByUserId(userId) >= limit) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                    "무료 플랜은 알림 규칙을 최대 " + limit + "개까지 만들 수 있어요. PRO로 업그레이드하면 무제한입니다.");
        }

        Alert alert = Alert.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .signalType(type)
                .timeframe(timeframe)
                .market(market)
                .enabled(true)
                .cooldownSec(cooldown)
                .build();
        return AlertDto.from(alertRepository.save(alert), instrument.getSymbol());
    }

    /** Best-effort instrument symbol for display; null if missing. */
    private String symbolOf(Long instrumentId) {
        try {
            return instrumentService.getById(instrumentId).getSymbol();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public AlertDto update(Long userId, Long alertId, Boolean enabled, Integer cooldownSec) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALERT_NOT_FOUND));
        if (!alert.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (cooldownSec != null && cooldownSec <= 0) {
            throw new ApiException(ErrorCode.INVALID_COOLDOWN);
        }
        if (enabled != null) {
            alert.setEnabled(enabled);
        }
        if (cooldownSec != null) {
            alert.setCooldownSec(cooldownSec);
        }
        return AlertDto.from(alertRepository.save(alert), symbolOf(alert.getInstrumentId()));
    }

    /**
     * 알림 규칙 삭제 (R62). 소유자만. 존재하지 않거나 남의 것이면 404(존재 여부 미노출).
     * FK(notifications.alert_id) 때문에 삭제 전 관련 알림의 링크를 끊는다(알림 이력은 보존).
     */
    public void delete(Long userId, Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALERT_NOT_FOUND));
        if (!alert.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.ALERT_NOT_FOUND);
        }
        notificationRepository.clearAlertId(alertId);
        alertRepository.delete(alert);
    }

    private SignalType parseSignalType(String signalType) {
        if (signalType == null) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "signal_type required");
        }
        try {
            return SignalType.valueOf(signalType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "Unknown signal_type: " + signalType);
        }
    }
}
