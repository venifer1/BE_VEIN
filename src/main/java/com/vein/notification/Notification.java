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

    /** Mark this notification as read at the given instant. */
    public void markRead(Instant now) {
        this.status = NotificationStatus.READ;
        this.readAt = now;
    }
}
