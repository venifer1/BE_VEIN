package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Pins the (market, timeframe) support matrix and code parsing (R111). Equities
 * intentionally support only day/3-day/week; crypto supports the full intraday
 * set. A silent change to these sets would break candle/scan endpoints.
 */
class TimeframeTest {

    @Test
    void fromCode_isCaseInsensitive() {
        assertThat(Timeframe.fromCode("4h")).isEqualTo(Timeframe.H4);
        assertThat(Timeframe.fromCode("4H")).isEqualTo(Timeframe.H4);
        assertThat(Timeframe.fromCode("1M")).isEqualTo(Timeframe.MN1);
    }

    @Test
    void fromCode_unknown_throwsUnsupportedTimeframe() {
        assertThatThrownBy(() -> Timeframe.fromCode("2h"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_TIMEFRAME);
    }

    @Test
    void duration_mapsCorrectly() {
        assertThat(Timeframe.H4.duration()).isEqualTo(Duration.ofHours(4));
        assertThat(Timeframe.W1.duration()).isEqualTo(Duration.ofDays(7));
        assertThat(Timeframe.MN1.duration()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void equities_supportOnlyDayAndUp() {
        for (String market : new String[] {"US", "KOSPI", "KOSDAQ"}) {
            assertThat(Timeframe.D1.isSupportedFor(market)).isTrue();
            assertThat(Timeframe.D3.isSupportedFor(market)).isTrue();
            assertThat(Timeframe.W1.isSupportedFor(market)).isTrue();
            assertThat(Timeframe.H1.isSupportedFor(market)).isFalse();
            assertThat(Timeframe.M15.isSupportedFor(market)).isFalse();
            assertThat(Timeframe.MN1.isSupportedFor(market)).isFalse();
        }
    }

    @Test
    void crypto_supportsFullSet() {
        assertThat(Timeframe.M15.isSupportedFor("CRYPTO")).isTrue();
        assertThat(Timeframe.MN1.isSupportedFor("CRYPTO")).isTrue();
    }

    @Test
    void nullOrUnknownMarket_defaultsToCrypto() {
        assertThat(Timeframe.supportedFor(null)).isEqualTo(Timeframe.supportedFor("CRYPTO"));
        assertThat(Timeframe.H1.isSupportedFor("UNKNOWN")).isTrue();
    }
}
