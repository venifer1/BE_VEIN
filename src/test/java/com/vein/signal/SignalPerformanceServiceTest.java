package com.vein.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 신호 성과 수익률 계산 (value/base - 1) * 100 순수 로직 회귀 보호(R154).
 * return_pct·MFE·MAE가 모두 이 함수를 공유. base가 0/null이거나 value null이면 null.
 */
class SignalPerformanceServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void positiveAndNegativeReturns() {
        assertThat(SignalPerformanceService.pct(bd("110"), bd("100"))).isEqualByComparingTo("10.0000");
        assertThat(SignalPerformanceService.pct(bd("90"), bd("100"))).isEqualByComparingTo("-10.0000");
    }

    @Test
    void zeroWhenValueEqualsBase() {
        assertThat(SignalPerformanceService.pct(bd("100"), bd("100"))).isEqualByComparingTo("0.0000");
    }

    @Test
    void roundsToFourDecimals() {
        // (1/3 - 1) * 100 = -66.66666... → -66.6667
        assertThat(SignalPerformanceService.pct(bd("1"), bd("3"))).isEqualByComparingTo("-66.6667");
    }

    @Test
    void returnsNullForNullOrZeroInputs() {
        assertThat(SignalPerformanceService.pct(null, bd("100"))).isNull();
        assertThat(SignalPerformanceService.pct(bd("100"), null)).isNull();
        assertThat(SignalPerformanceService.pct(bd("100"), bd("0"))).isNull();
    }
}
