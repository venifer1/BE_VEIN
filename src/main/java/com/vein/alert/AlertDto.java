package com.vein.alert;

/**
 * Alert representation returned to clients (snake_case via global Jackson config).
 */
public record AlertDto(String alertId, boolean enabled, Long instrumentId, String symbol,
                       String signalType, String timeframe, String market, Integer cooldownSec) {

    public static AlertDto from(Alert alert, String symbol) {
        return new AlertDto(
                String.valueOf(alert.getId()),
                alert.isEnabled(),
                alert.getInstrumentId(),
                symbol,
                alert.getSignalType().name(),
                alert.getTimeframe(),
                alert.getMarket(),
                alert.getCooldownSec());
    }
}
