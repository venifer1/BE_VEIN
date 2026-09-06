package com.vein.alert;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.instrument.InstrumentService;
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

    public AlertService(AlertRepository alertRepository, InstrumentService instrumentService) {
        this.alertRepository = alertRepository;
        this.instrumentService = instrumentService;
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
