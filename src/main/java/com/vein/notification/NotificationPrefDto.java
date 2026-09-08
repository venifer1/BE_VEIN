package com.vein.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 알림 환경설정 (R42, snake_case via global Jackson). 조용한 시간은 KST 시(0-23).
 * 요청/응답 동일 형태. 시각은 0-23 범위 검증(R43) — 벗어나면 400 VALIDATION_ERROR.
 */
public record NotificationPrefDto(
        boolean quietEnabled,
        @Min(0) @Max(23) int quietStartHour,
        @Min(0) @Max(23) int quietEndHour) {

    public static NotificationPrefDto from(NotificationPref p) {
        return new NotificationPrefDto(p.isQuietEnabled(), p.getQuietStartHour(), p.getQuietEndHour());
    }
}
