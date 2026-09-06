package com.vein.tvl;

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
 * A DefiLlama TVL snapshot row (V8 {@code tvl_snapshots}). entity_type
 * PROTOCOL|CHAIN; the latest collected_at batch is the current view.
 */
@Entity
@Table(name = "tvl_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TvlSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 16)
    private String entityType;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 128)
    private String category;

    @Column(columnDefinition = "text")
    private String chains;

    @Column(precision = 30, scale = 2)
    private BigDecimal tvl;

    @Column(precision = 30, scale = 2)
    private BigDecimal mcap;

    @Column(name = "change_1d", precision = 12, scale = 4)
    private BigDecimal change1d;

    @Column(name = "change_7d", precision = 12, scale = 4)
    private BigDecimal change7d;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private TvlSnapshot(String entityType, String externalId, String name, String category,
                        String chains, BigDecimal tvl, BigDecimal mcap, BigDecimal change1d,
                        BigDecimal change7d, Instant collectedAt) {
        this.entityType = entityType;
        this.externalId = externalId;
        this.name = name;
        this.category = category;
        this.chains = chains;
        this.tvl = tvl;
        this.mcap = mcap;
        this.change1d = change1d;
        this.change7d = change7d;
        this.collectedAt = collectedAt;
    }

    public static TvlSnapshot of(String entityType, String externalId, String name, String category,
                                 String chains, BigDecimal tvl, BigDecimal mcap, BigDecimal change1d,
                                 BigDecimal change7d, Instant collectedAt) {
        return new TvlSnapshot(entityType, externalId, name, category, chains, tvl, mcap,
                change1d, change7d, collectedAt);
    }
}
