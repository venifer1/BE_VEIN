package com.vein.signal;

import java.math.BigDecimal;
import java.time.Instant;

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
import lombok.Setter;

/**
 * signal_performance (기획서 §31 / V11). How a {@link PatternSignal} actually
 * performed over a fixed wall-clock {@code horizon} after detection. One row per
 * (signal, horizon), upserted idempotently by the performance job.
 */
@Entity
@Table(name = "signal_performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SignalPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    /** Horizon code: 1h | 4h | 1d | 3d | 7d. */
    @Column(nullable = false, length = 4)
    private String horizon;

    /** Close of the candle at/after detected_at + horizon. */
    @Setter
    @Column(name = "price")
    private BigDecimal price;

    /** (price / detected_price - 1) * 100. */
    @Setter
    @Column(name = "return_pct")
    private BigDecimal returnPct;

    /** Max favorable excursion: (max high in window / detected_price - 1) * 100. */
    @Setter
    @Column(name = "mfe_pct")
    private BigDecimal mfePct;

    /** Max adverse excursion: (min low in window / detected_price - 1) * 100. */
    @Setter
    @Column(name = "mae_pct")
    private BigDecimal maePct;

    @Setter
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    static SignalPerformance create(Long signalId, String horizon, BigDecimal price,
                                    BigDecimal returnPct, BigDecimal mfePct, BigDecimal maePct) {
        return SignalPerformance.builder()
                .signalId(signalId)
                .horizon(horizon)
                .price(price)
                .returnPct(returnPct)
                .mfePct(mfePct)
                .maePct(maePct)
                .build();
    }
}
