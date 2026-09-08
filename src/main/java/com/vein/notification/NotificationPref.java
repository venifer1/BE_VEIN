package com.vein.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 알림 환경설정 (R42). 현재는 조용한 시간(Quiet Hours)만 담는다 — 이 창 동안에는
 * 새 알림을 만들지 않아, 자는 사이 쌓이는 핑을 줄인다. 시각은 <b>KST 시(0-23)</b>이며
 * {@code start==end}면 창 없음, {@code start>end}면 자정을 넘는 창으로 해석한다.
 */
@Entity
@Table(name = "notification_prefs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPref {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "quiet_enabled", nullable = false)
    private boolean quietEnabled;

    @Column(name = "quiet_start_hour", nullable = false)
    private int quietStartHour;

    @Column(name = "quiet_end_hour", nullable = false)
    private int quietEndHour;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationPref defaults(Long userId) {
        NotificationPref p = new NotificationPref();
        p.userId = userId;
        p.quietEnabled = false;
        p.quietStartHour = 22;
        p.quietEndHour = 8;
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(boolean enabled, int startHour, int endHour) {
        this.quietEnabled = enabled;
        this.quietStartHour = clampHour(startHour);
        this.quietEndHour = clampHour(endHour);
        this.updatedAt = Instant.now();
    }

    private static int clampHour(int h) {
        if (h < 0) {
            return 0;
        }
        return Math.min(h, 23);
    }
}
