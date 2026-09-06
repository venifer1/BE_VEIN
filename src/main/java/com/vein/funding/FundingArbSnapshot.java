package com.vein.funding;

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
 * A funding-arbitrage snapshot row (V8 {@code funding_arb_snapshots}): Bybit perp
 * funding vs Upbit KRW spot. The latest collected_at batch is the current view.
 */
@Entity
@Table(name = "funding_arb_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingArbSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(length = 128)
    private String name;

    @Column(name = "funding_pct", precision = 12, scale = 6)
    private BigDecimal fundingPct;

    @Column(name = "upbit_price", precision = 30, scale = 10)
    private BigDecimal upbitPrice;

    @Column(name = "bybit_price", precision = 30, scale = 10)
    private BigDecimal bybitPrice;

    @Column(name = "next_funding_at")
    private Instant nextFundingAt;

    @Column(name = "expected_1x_pct", precision = 12, scale = 6)
    private BigDecimal expected1xPct;

    @Column(name = "expected_2x_pct", precision = 12, scale = 6)
    private BigDecimal expected2xPct;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private FundingArbSnapshot(String symbol, String name, BigDecimal fundingPct, BigDecimal upbitPrice,
                               BigDecimal bybitPrice, Instant nextFundingAt, BigDecimal expected1xPct,
                               BigDecimal expected2xPct, Instant collectedAt) {
        this.symbol = symbol;
        this.name = name;
        this.fundingPct = fundingPct;
        this.upbitPrice = upbitPrice;
        this.bybitPrice = bybitPrice;
        this.nextFundingAt = nextFundingAt;
        this.expected1xPct = expected1xPct;
        this.expected2xPct = expected2xPct;
        this.collectedAt = collectedAt;
    }

    public static FundingArbSnapshot of(String symbol, String name, BigDecimal fundingPct,
                                        BigDecimal upbitPrice, BigDecimal bybitPrice, Instant nextFundingAt,
                                        BigDecimal expected1xPct, BigDecimal expected2xPct, Instant collectedAt) {
        return new FundingArbSnapshot(symbol, name, fundingPct, upbitPrice, bybitPrice, nextFundingAt,
                expected1xPct, expected2xPct, collectedAt);
    }
}
