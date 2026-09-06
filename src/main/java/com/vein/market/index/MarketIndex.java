package com.vein.market.index;

import java.math.BigDecimal;
import java.time.Instant;

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
 * A single market gauge snapshot (V6 {@code market_indices}). One row per
 * (index_key, collected_at); the latest per key is the current value.
 */
@Entity
@Table(name = "market_indices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_key", nullable = false, length = 24)
    private String indexKey;

    @Column(precision = 24, scale = 8)
    private BigDecimal value;

    @Column(length = 32)
    private String classification;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private MarketIndex(String indexKey, BigDecimal value, String classification, Instant collectedAt) {
        this.indexKey = indexKey;
        this.value = value;
        this.classification = classification;
        this.collectedAt = collectedAt;
    }

    public static MarketIndex of(String indexKey, BigDecimal value, String classification,
                                 Instant collectedAt) {
        return new MarketIndex(indexKey, value, classification, collectedAt);
    }
}
