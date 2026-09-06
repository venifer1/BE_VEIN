package com.vein.signal;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * signal_evidence (부록 B / V2). evidence_type ∈
 * PIVOT_0|PIVOT_A|PIVOT_B|TREND_UPPER|TREND_LOWER|C_TARGET.
 */
@Entity
@Table(name = "signal_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SignalEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column
    private BigDecimal price;

    @Column(name = "candle_time")
    private Instant candleTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;
}
