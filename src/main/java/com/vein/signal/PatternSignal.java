package com.vein.signal;

import java.math.BigDecimal;
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
 * pattern_signals (부록 B / V2). signal_key = algorithm_version|rule_id|instrument_id|timeframe|anchor_candle_time.
 */
@Entity
@Table(name = "pattern_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PatternSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(length = 16)
    private String market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalType type;

    @Column(length = 16)
    private String subtype;

    @Column(nullable = false)
    private String timeframe;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalStatus status;

    @Column
    private BigDecimal score;

    @Column(name = "c_target")
    private BigDecimal cTarget;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "anchor_candle_time", nullable = false)
    private Instant anchorCandleTime;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "invalidation_rule")
    private String invalidationRule;

    @Column(name = "invalidation_price")
    private BigDecimal invalidationPrice;

    @Setter
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "signal_key", nullable = false, unique = true)
    private String signalKey;

    /** Build the idempotency key per §10.1. */
    public static String buildSignalKey(String algorithmVersion, String ruleId, Long instrumentId,
                                        String timeframe, Instant anchorCandleTime) {
        return algorithmVersion + "|" + ruleId + "|" + instrumentId + "|" + timeframe + "|"
                + anchorCandleTime.toString();
    }
}
