package com.vein.supply;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 유통량 비율 계산(circulating / (max ?: total) * 100) 순수 로직 회귀 보호(R153).
 * supply_tab.py 이식. max가 우선, 없거나 0이면 total, 둘 다 없으면 null.
 */
class SupplyServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void usesMaxSupplyAsDenominatorWhenPositive() {
        // 18M / 21M = 85.7143%
        assertThat(SupplyService.circulatingPct(bd("18000000"), bd("21000000"), null))
                .isEqualByComparingTo("85.7143");
    }

    @Test
    void fallsBackToTotalWhenMaxMissingOrZero() {
        assertThat(SupplyService.circulatingPct(bd("50"), null, bd("200")))
                .isEqualByComparingTo("25.0000");
        // max=0 은 무효 → total 사용
        assertThat(SupplyService.circulatingPct(bd("50"), bd("0"), bd("100")))
                .isEqualByComparingTo("50.0000");
    }

    @Test
    void returnsNullWhenCirculatingMissing() {
        assertThat(SupplyService.circulatingPct(null, bd("21000000"), bd("21000000"))).isNull();
    }

    @Test
    void returnsNullWhenNoUsableDenominator() {
        assertThat(SupplyService.circulatingPct(bd("50"), null, null)).isNull();
        assertThat(SupplyService.circulatingPct(bd("50"), bd("0"), bd("0"))).isNull();
    }

    @Test
    void allowsOverHundredPercentWhenCirculatingExceedsDenominator() {
        // 방어적: 데이터 이상 시 100% 초과도 그대로 표현
        assertThat(SupplyService.circulatingPct(bd("250"), null, bd("200")))
                .isEqualByComparingTo("125.0000");
    }
}
