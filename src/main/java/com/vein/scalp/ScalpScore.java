package com.vein.scalp;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A scalping score snapshot (V8 {@code scalp_scores}). {@code components} is a
 * JSON object (JSONB) with per-factor scores + microstructure detail. The latest
 * collected_at batch is the current ranking.
 */
@Entity
@Table(name = "scalp_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScalpScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "scalp_score", precision = 6, scale = 2)
    private BigDecimal scalpScore;

    @Column(name = "spread_ticks", precision = 12, scale = 4)
    private BigDecimal spreadTicks;

    @Column(precision = 12, scale = 4)
    private BigDecimal tps;

    @Column(name = "micro_vol", precision = 12, scale = 4)
    private BigDecimal microVol;

    @Column(name = "ob_imbalance", precision = 14, scale = 6)
    private BigDecimal obImbalance;

    @Column(name = "wall_state", length = 16)
    private String wallState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String components;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private ScalpScore(String symbol, BigDecimal scalpScore, BigDecimal spreadTicks, BigDecimal tps,
                       BigDecimal microVol, BigDecimal obImbalance, String wallState, String components,
                       Instant collectedAt) {
        this.symbol = symbol;
        this.scalpScore = scalpScore;
        this.spreadTicks = spreadTicks;
        this.tps = tps;
        this.microVol = microVol;
        this.obImbalance = obImbalance;
        this.wallState = wallState;
        this.components = components;
        this.collectedAt = collectedAt;
    }

    public static ScalpScore of(String symbol, BigDecimal scalpScore, BigDecimal spreadTicks,
                                BigDecimal tps, BigDecimal microVol, BigDecimal obImbalance,
                                String wallState, String components, Instant collectedAt) {
        return new ScalpScore(symbol, scalpScore, spreadTicks, tps, microVol, obImbalance,
                wallState, components, collectedAt);
    }
}
