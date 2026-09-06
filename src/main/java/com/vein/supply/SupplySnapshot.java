package com.vein.supply;

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
 * A CoinGecko circulating-supply snapshot row (V8 {@code supply_snapshots}).
 * The latest collected_at batch is the current view.
 */
@Entity
@Table(name = "supply_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coingecko_id", nullable = false, length = 96)
    private String coingeckoId;

    @Column(name = "rank")
    private Integer rank;

    @Column(length = 128)
    private String name;

    @Column(length = 32)
    private String symbol;

    @Column(name = "price_usd", precision = 30, scale = 10)
    private BigDecimal priceUsd;

    @Column(name = "market_cap", precision = 30, scale = 2)
    private BigDecimal marketCap;

    @Column(precision = 40, scale = 4)
    private BigDecimal circulating;

    @Column(name = "total_supply", precision = 40, scale = 4)
    private BigDecimal totalSupply;

    @Column(name = "max_supply", precision = 40, scale = 4)
    private BigDecimal maxSupply;

    @Column(name = "circulating_pct", precision = 12, scale = 4)
    private BigDecimal circulatingPct;

    @Column(precision = 30, scale = 2)
    private BigDecimal fdv;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private SupplySnapshot(String coingeckoId, Integer rank, String name, String symbol,
                           BigDecimal priceUsd, BigDecimal marketCap, BigDecimal circulating,
                           BigDecimal totalSupply, BigDecimal maxSupply, BigDecimal circulatingPct,
                           BigDecimal fdv, Instant collectedAt) {
        this.coingeckoId = coingeckoId;
        this.rank = rank;
        this.name = name;
        this.symbol = symbol;
        this.priceUsd = priceUsd;
        this.marketCap = marketCap;
        this.circulating = circulating;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.circulatingPct = circulatingPct;
        this.fdv = fdv;
        this.collectedAt = collectedAt;
    }

    public static SupplySnapshot of(String coingeckoId, Integer rank, String name, String symbol,
                                    BigDecimal priceUsd, BigDecimal marketCap, BigDecimal circulating,
                                    BigDecimal totalSupply, BigDecimal maxSupply, BigDecimal circulatingPct,
                                    BigDecimal fdv, Instant collectedAt) {
        return new SupplySnapshot(coingeckoId, rank, name, symbol, priceUsd, marketCap, circulating,
                totalSupply, maxSupply, circulatingPct, fdv, collectedAt);
    }
}
