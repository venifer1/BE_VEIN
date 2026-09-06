package com.vein.strategy;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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
 * A single re-run of a saved {@link Strategy}'s backtest, capturing the metrics
 * snapshot at that point in time so performance history accrues. Maps the
 * Flyway-owned {@code strategy_runs} table (V15).
 *
 * <p>The {@code metrics} JSONB column reuses the project's
 * {@code @JdbcTypeCode(SqlTypes.JSON) JsonNode} approach (cf. {@link Strategy});
 * {@code trade_count}/{@code total_return_pct}/{@code win_rate} are denormalized
 * out of that snapshot for quick history charts.
 */
@Entity
@Table(name = "strategy_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class StrategyRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode metrics;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(name = "total_return_pct")
    private BigDecimal totalReturnPct;

    @Column(name = "win_rate")
    private BigDecimal winRate;

    @Column(name = "run_at", insertable = false, updatable = false)
    private Instant runAt;
}
