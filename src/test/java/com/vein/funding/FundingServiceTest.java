package com.vein.funding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure funding-arbitrage expected-profit calc (R109).
 *
 * <p>Pins the semantics that tripped up R107: {@code expectedProfitPct(pct, count)}
 * = fundingPct × <b>count</b> (number of funding collections) − roundtrip fees.
 * "1x/2x" in the UI means 1/2 funding cycles, NOT leverage. Roundtrip fee =
 * 2×(Upbit 0.05% + Bybit 0.055%) = 0.21%.
 */
class FundingServiceTest {

    @Test
    void oneCycle_subtractsRoundtripFee() {
        // 0.30 × 1 − 0.21 = 0.09
        assertThat(FundingService.expectedProfitPct(new BigDecimal("0.30"), 1))
                .isEqualByComparingTo("0.09");
    }

    @Test
    void twoCycles_doublesFundingBeforeFee() {
        // 0.30 × 2 − 0.21 = 0.39
        assertThat(FundingService.expectedProfitPct(new BigDecimal("0.30"), 2))
                .isEqualByComparingTo("0.39");
    }

    @Test
    void moreCycles_yieldMore() {
        BigDecimal one = FundingService.expectedProfitPct(new BigDecimal("0.10"), 1);
        BigDecimal two = FundingService.expectedProfitPct(new BigDecimal("0.10"), 2);
        assertThat(two).isGreaterThan(one);
    }

    @Test
    void negativeFunding_canBeNegativeAfterFee() {
        // -0.10 × 1 − 0.21 = -0.31
        assertThat(FundingService.expectedProfitPct(new BigDecimal("-0.10"), 1))
                .isEqualByComparingTo("-0.31");
    }

    @Test
    void countClampedAtZero() {
        // negative count -> 0 cycles -> only the fee remains
        assertThat(FundingService.expectedProfitPct(new BigDecimal("0.30"), -1))
                .isEqualByComparingTo("-0.21");
    }

    @Test
    void nullFunding_returnsNull() {
        assertThat(FundingService.expectedProfitPct(null, 1)).isNull();
    }
}
