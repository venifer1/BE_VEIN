package com.vein.alert;

import java.time.Instant;

import com.vein.signal.SignalType;

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
 * User-configured alert binding an instrument + signal type to in-app notifications.
 * Maps the Flyway-owned {@code alerts} table.
 */
@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 16)
    private SignalType signalType;

    @Column(length = 4)
    private String timeframe;

    @Column(length = 16)
    private String market;

    @Setter
    @Column(nullable = false)
    private boolean enabled;

    @Setter
    @Column(name = "cooldown_sec", nullable = false)
    private int cooldownSec;

    @Setter
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** True when a previous trigger is still within the cooldown window. */
    public boolean inCooldown(Instant now) {
        return lastTriggeredAt != null && now.isBefore(lastTriggeredAt.plusSeconds(cooldownSec));
    }
}
