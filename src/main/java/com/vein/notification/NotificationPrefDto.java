package com.vein.notification;

/**
 * 알림 환경설정 (R42, snake_case via global Jackson). 조용한 시간은 KST 시(0-23).
 * 요청/응답 동일 형태.
 */
public record NotificationPrefDto(boolean quietEnabled, int quietStartHour, int quietEndHour) {

    public static NotificationPrefDto from(NotificationPref p) {
        return new NotificationPrefDto(p.isQuietEnabled(), p.getQuietStartHour(), p.getQuietEndHour());
    }
}
