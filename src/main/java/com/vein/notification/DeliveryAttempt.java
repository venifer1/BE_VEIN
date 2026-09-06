package com.vein.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-channel delivery attempt for a notification. Maps the Flyway-owned
 * {@code delivery_attempts} table.
 */
@Entity
@Table(name = "delivery_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "error_code", length = 48)
    private String errorCode;

    @Column(name = "attempted_at", insertable = false, updatable = false)
    private Instant attemptedAt;
}
