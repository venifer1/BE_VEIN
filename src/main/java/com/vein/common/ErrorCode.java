package com.vein.common;

import org.springframework.http.HttpStatus;

/**
 * Error code catalog (부록 C-3 / 표 15).
 */
public enum ErrorCode {
    // Auth
    AUTH_INVALID(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    USER_NOT_APPROVED(HttpStatus.FORBIDDEN, "User not approved"),
    USER_LOCKED(HttpStatus.FORBIDDEN, "User locked"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token expired"),
    TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected"),

    // Instrument / Candle
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "Invalid query"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    UNSUPPORTED_TIMEFRAME(HttpStatus.BAD_REQUEST, "Unsupported timeframe"),
    DATA_DELAYED(HttpStatus.OK, "Data delayed"),

    // Signal
    INVALID_FILTER(HttpStatus.BAD_REQUEST, "Invalid filter"),
    DATA_PARTIAL(HttpStatus.OK, "Partial data"),
    SIGNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Signal not found"),
    SIGNAL_RETIRED(HttpStatus.GONE, "Signal retired"),

    // Watchlist
    WATCHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "Watchlist not found"),
    ALREADY_EXISTS(HttpStatus.CONFLICT, "Already exists"),
    INSTRUMENT_INACTIVE(HttpStatus.CONFLICT, "Instrument inactive"),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Item not found"),

    // Alert / Notification
    INVALID_COOLDOWN(HttpStatus.BAD_REQUEST, "Invalid cooldown"),
    DUPLICATE_ALERT(HttpStatus.CONFLICT, "Duplicate alert"),
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "Alert not found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid cursor"),

    // Strategy
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND, "Strategy not found"),

    // System
    STATUS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Status unavailable"),

    // Public / rate limit
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // Generic
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
