package com.vein.market.candle;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite key for {@link Candle}: (instrument_id, timeframe, open_time, provider).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "timeframe", nullable = false, length = 4)
    private String timeframe;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    public CandleId(Long instrumentId, String timeframe, Instant openTime, String provider) {
        this.instrumentId = instrumentId;
        this.timeframe = timeframe;
        this.openTime = openTime;
        this.provider = provider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CandleId other)) {
            return false;
        }
        return Objects.equals(instrumentId, other.instrumentId)
                && Objects.equals(timeframe, other.timeframe)
                && Objects.equals(openTime, other.openTime)
                && Objects.equals(provider, other.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentId, timeframe, openTime, provider);
    }
}
