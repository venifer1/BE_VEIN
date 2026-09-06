package com.vein.market.candle;

import java.math.BigDecimal;
import java.time.Instant;

import com.vein.common.Timeframe;
import com.vein.pattern.core.Bar;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OHLCV candle (부록 D / 표 16-17). Maps the Flyway-owned {@code candles}
 * table and implements {@link Bar} for the pattern detectors.
 */
@Entity
@Table(name = "candles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candle implements Bar {

    @EmbeddedId
    private CandleId id;

    @Column(name = "open", precision = 24, scale = 8)
    private BigDecimal open;

    @Column(name = "high", precision = 24, scale = 8)
    private BigDecimal high;

    @Column(name = "low", precision = 24, scale = 8)
    private BigDecimal low;

    @Column(name = "close", precision = 24, scale = 8)
    private BigDecimal close;

    @Column(name = "volume", precision = 28, scale = 8)
    private BigDecimal volume;

    @Column(name = "is_final", nullable = false)
    private boolean isFinal;

    @Column(name = "collected_at", nullable = false, insertable = false, updatable = false)
    private Instant collectedAt;

    private Candle(CandleId id, BigDecimal open, BigDecimal high, BigDecimal low,
                   BigDecimal close, BigDecimal volume, boolean isFinal) {
        this.id = id;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.isFinal = isFinal;
    }

    /** All-fields factory. */
    public static Candle of(Long instrumentId, Timeframe tf, Instant openTime, String provider,
                            BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                            BigDecimal volume, boolean isFinal) {
        return new Candle(
                new CandleId(instrumentId, tf.code(), openTime, provider),
                open, high, low, close, volume, isFinal);
    }

    // --- Bar ---

    @Override
    public Instant openTime() {
        return id.getOpenTime();
    }

    @Override
    public BigDecimal open() {
        return open;
    }

    @Override
    public BigDecimal high() {
        return high;
    }

    @Override
    public BigDecimal low() {
        return low;
    }

    @Override
    public BigDecimal close() {
        return close;
    }

    @Override
    public BigDecimal volume() {
        return volume;
    }
}
