package com.vein.market.kimchi;

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
 * Kimchi premium snapshot (V6 {@code kimchi_premium}):
 * {@code premium_pct = (upbit_price / (binance_price * usdkrw) - 1) * 100}.
 */
@Entity
@Table(name = "kimchi_premium")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KimchiPremium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "upbit_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal upbitPrice;

    @Column(name = "binance_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal binancePrice;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal usdkrw;

    @Column(name = "premium_pct", nullable = false, precision = 12, scale = 4)
    private BigDecimal premiumPct;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private KimchiPremium(Long instrumentId, BigDecimal upbitPrice, BigDecimal binancePrice,
                          BigDecimal usdkrw, BigDecimal premiumPct, Instant collectedAt) {
        this.instrumentId = instrumentId;
        this.upbitPrice = upbitPrice;
        this.binancePrice = binancePrice;
        this.usdkrw = usdkrw;
        this.premiumPct = premiumPct;
        this.collectedAt = collectedAt;
    }

    public static KimchiPremium of(Long instrumentId, BigDecimal upbitPrice, BigDecimal binancePrice,
                                   BigDecimal usdkrw, BigDecimal premiumPct, Instant collectedAt) {
        return new KimchiPremium(instrumentId, upbitPrice, binancePrice, usdkrw, premiumPct, collectedAt);
    }
}
