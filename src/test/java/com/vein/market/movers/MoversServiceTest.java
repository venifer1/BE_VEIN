package com.vein.market.movers;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 주식 등락률 계산 (close/prevClose - 1) * 100 순수 로직 회귀 보호(R174).
 * R174에서 equitySnapshot 루프 인라인 계산을 순수 함수로 추출(동작 보존).
 */
class MoversServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void positiveAndNegativeChange() {
        assertThat(MoversService.changePct(bd("110"), bd("100"))).isEqualByComparingTo("10.0000");
        assertThat(MoversService.changePct(bd("90"), bd("100"))).isEqualByComparingTo("-10.0000");
    }

    @Test
    void zeroWhenUnchanged() {
        assertThat(MoversService.changePct(bd("100"), bd("100"))).isEqualByComparingTo("0.0000");
    }

    @Test
    void roundsToFourDecimals() {
        // 1/3 - 1 = -0.6666... → -66.6667%
        assertThat(MoversService.changePct(bd("1"), bd("3"))).isEqualByComparingTo("-66.6667");
    }

    @Test
    void nullForInvalidInputs() {
        assertThat(MoversService.changePct(null, bd("100"))).isNull();
        assertThat(MoversService.changePct(bd("100"), null)).isNull();
        assertThat(MoversService.changePct(bd("100"), bd("0"))).isNull();
    }
}
