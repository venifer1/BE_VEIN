package com.vein.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/**
 * 스풀링(R46)의 창 종료 시각 계산(순수 정적). KST(UTC+9), 자정을 넘는 창 포함. DB 불필요.
 */
class NotificationSpoolWindowTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static Instant kst(int y, int mo, int d, int h) {
        return ZonedDateTime.of(y, mo, d, h, 0, 0, 0, KST).toInstant();
    }

    @Test
    void nullWhenOutsideWindow() {
        // 창 [9,18), 지금 20시 → 창 밖
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 20), 9, 18)).isNull();
    }

    @Test
    void nullWhenWindowEmpty() {
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 3), 8, 8)).isNull();
    }

    @Test
    void normalWindowEndsSameDay() {
        // 창 [9,18), 지금 10시 → 오늘 18시 KST에 끝
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 10), 9, 18))
                .isEqualTo(kst(2026, 9, 12, 18));
    }

    @Test
    void overnightWindowEveningEndsNextMorning() {
        // 창 [22,8) 자정 넘김, 지금 23시(저녁) → 내일 아침 8시 KST에 끝
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 23), 22, 8))
                .isEqualTo(kst(2026, 9, 13, 8));
    }

    @Test
    void overnightWindowMorningEndsSameDay() {
        // 창 [22,8) 자정 넘김, 지금 3시(새벽) → 오늘 아침 8시 KST에 끝
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 3), 22, 8))
                .isEqualTo(kst(2026, 9, 12, 8));
    }

    @Test
    void startHourIsInsideWindow() {
        // 시작 시각(22시)은 창 안 — 보류되어 종료시각이 나와야 함
        assertThat(NotificationPrefService.windowEnd(kst(2026, 9, 12, 22), 22, 8))
                .isEqualTo(kst(2026, 9, 13, 8));
    }
}
