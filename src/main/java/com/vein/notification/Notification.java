package com.vein.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-app notification. Maps the Flyway-owned {@code notifications} table.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "alert_id")
    private Long alertId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "read_at")
    private Instant readAt;

    /**
     * 스풀링(R46): 조용한 시간에 도착한 알림의 보류 만료 시각. 이 시각 전까지는 읽기 모델에서
     * 제외돼 핑/배지가 뜨지 않고, 지나면 자연히 노출된다. NULL이면 보류 아님(즉시 활성).
     */
    @Column(name = "held_until")
    private Instant heldUntil;

    /** Mark this notification as read at the given instant. */
    public void markRead(Instant now) {
        this.status = NotificationStatus.READ;
        this.readAt = now;
    }
}
