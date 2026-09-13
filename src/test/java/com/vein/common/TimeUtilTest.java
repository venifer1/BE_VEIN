package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * ISO round-trip + freshness threshold (R115). Freshness drives the 최신/지연
 * badge: DELAYED once elapsed exceeds 2× the timeframe duration. All business
 * times are UTC/ISO-8601 with a Z suffix.
 */
class TimeUtilTest {

    @Test
    void iso_isNullSafe_andRoundTrips() {
        assertThat(TimeUtil.toIso(null)).isNull();
        assertThat(TimeUtil.parseIso(null)).isNull();
        Instant t = Instant.parse("2026-06-12T15:00:00Z");
        String iso = TimeUtil.toIso(t);
        assertThat(iso).endsWith("Z");
        assertThat(TimeUtil.parseIso(iso)).isEqualTo(t);
    }

    @Test
    void freshness_nullCollected_isDelayed() {
        assertThat(TimeUtil.freshness(null, Timeframe.H1, Instant.parse("2026-06-12T15:00:00Z")))
                .isEqualTo(Freshness.DELAYED);
    }

    @Test
    void freshness_withinTwiceTimeframe_isFresh() {
        Instant now = Instant.parse("2026-06-12T15:00:00Z");
        Instant collected = now.minus(Duration.ofMinutes(90)); // < 2×1h
        assertThat(TimeUtil.freshness(collected, Timeframe.H1, now)).isEqualTo(Freshness.FRESH);
    }

    @Test
    void freshness_beyondTwiceTimeframe_isDelayed() {
        Instant now = Instant.parse("2026-06-12T15:00:00Z");
        Instant collected = now.minus(Duration.ofHours(3)); // > 2×1h
        assertThat(TimeUtil.freshness(collected, Timeframe.H1, now)).isEqualTo(Freshness.DELAYED);
    }

    @Test
    void freshness_exactlyTwiceTimeframe_isFresh() {
        // boundary is exclusive: elapsed == 2×tf is still FRESH (compareTo > 0)
        Instant now = Instant.parse("2026-06-12T15:00:00Z");
        Instant collected = now.minus(Duration.ofHours(2)); // == 2×1h
        assertThat(TimeUtil.freshness(collected, Timeframe.H1, now)).isEqualTo(Freshness.FRESH);
    }
}
