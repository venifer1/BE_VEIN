package com.vein.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.vein.common.Freshness;

/**
 * 시장 데이터 신선도(FRESH/DELAYED) 판정 순수 로직 회귀 보호(R170). null·창 초과는 DELAYED.
 */
class MarketControllerTest {

    @Test
    void nullCollectedAtIsDelayed() {
        assertThat(MarketController.freshness(null, Duration.ofMinutes(3))).isEqualTo(Freshness.DELAYED);
    }

    @Test
    void recentWithinWindowIsFresh() {
        assertThat(MarketController.freshness(Instant.now(), Duration.ofHours(1)))
                .isEqualTo(Freshness.FRESH);
    }

    @Test
    void olderThanWindowIsDelayed() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        assertThat(MarketController.freshness(twoHoursAgo, Duration.ofHours(1)))
                .isEqualTo(Freshness.DELAYED);
    }
}
