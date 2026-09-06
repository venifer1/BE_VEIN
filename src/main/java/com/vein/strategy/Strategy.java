package com.vein.strategy;

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
 * A user-saved backtest configuration + result snapshot (기획서 §12 seed).
 * Maps the Flyway-owned {@code strategies} table.
 *
 * <p>JSONB columns ({@code params}, {@code metrics}) reuse the project's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} approach (cf. signal_evidence.payload,
 * audit_logs.detail) but bind to a Jackson {@link JsonNode} rather than a
 * {@code String} so values round-trip as real JSON instead of an encoded
 * string.
 */
@Entity
@Table(name = "strategies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(length = 16)
    private String market;

    @Column(length = 8)
    private String timeframe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metrics;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Update the cached latest-result metrics snapshot (performance tracking). */
    public void updateMetrics(JsonNode metrics) {
        this.metrics = metrics;
    }
}
