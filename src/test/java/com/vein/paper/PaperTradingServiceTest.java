package com.vein.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 모의투자 금액/수량/수익률 문자열 표기 순수 로직 회귀 보호(R156).
 * money·qty=소수 8자리, pct=소수 4자리, 모두 HALF_UP 후 후행 0 제거(계약: 숫자는 String).
 */
class PaperTradingServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void moneyScalesTo8AndStripsTrailingZeros() {
        assertThat(PaperTradingService.money(bd("100.50000000"))).isEqualTo("100.5");
        assertThat(PaperTradingService.money(bd("2"))).isEqualTo("2");
        assertThat(PaperTradingService.money(bd("0.00000001"))).isEqualTo("0.00000001");
        // 8자리 초과는 HALF_UP 반올림
        assertThat(PaperTradingService.money(bd("1.123456789"))).isEqualTo("1.12345679");
    }

    @Test
    void qtyBehavesLikeMoney() {
        assertThat(PaperTradingService.qty(bd("3.14000000"))).isEqualTo("3.14");
        assertThat(PaperTradingService.qty(bd("10"))).isEqualTo("10");
    }

    @Test
    void pctScalesTo4AndStripsTrailingZeros() {
        assertThat(PaperTradingService.pct(bd("10.2500"))).isEqualTo("10.25");
        assertThat(PaperTradingService.pct(bd("10"))).isEqualTo("10");
        assertThat(PaperTradingService.pct(bd("-5.5"))).isEqualTo("-5.5");
        assertThat(PaperTradingService.pct(bd("0.33335"))).isEqualTo("0.3334"); // HALF_UP
    }

    @Test
    void zeroRendersAsPlainZero() {
        assertThat(PaperTradingService.money(bd("0"))).isEqualTo("0");
        assertThat(PaperTradingService.pct(bd("0.0000"))).isEqualTo("0");
    }
}
