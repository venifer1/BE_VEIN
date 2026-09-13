package com.vein.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 조건검색 비교 로직(R136 추출) 회귀 보호. 스캐너 매칭의 핵심 연산자 평가.
 */
class ConditionScannerServiceTest {

    private static final BigDecimal A = new BigDecimal("30");
    private static final BigDecimal B = new BigDecimal("70");

    @Test
    void lessThan() {
        assertThat(ConditionScannerService.compare(A, "<", B)).isTrue();
        assertThat(ConditionScannerService.compare(B, "<", A)).isFalse();
    }

    @Test
    void lessOrEqual_boundary() {
        assertThat(ConditionScannerService.compare(A, "<=", A)).isTrue();
        assertThat(ConditionScannerService.compare(B, "<=", A)).isFalse();
    }

    @Test
    void greaterThan() {
        assertThat(ConditionScannerService.compare(B, ">", A)).isTrue();
        assertThat(ConditionScannerService.compare(A, ">", B)).isFalse();
    }

    @Test
    void greaterOrEqual_boundary() {
        assertThat(ConditionScannerService.compare(A, ">=", A)).isTrue();
        assertThat(ConditionScannerService.compare(A, ">=", B)).isFalse();
    }

    @Test
    void nullOperandOrUnknownOperator_isFalse() {
        assertThat(ConditionScannerService.compare(null, "<", B)).isFalse();
        assertThat(ConditionScannerService.compare(A, "<", null)).isFalse();
        assertThat(ConditionScannerService.compare(A, "==", B)).isFalse();
        assertThat(ConditionScannerService.compare(A, null, B)).isFalse();
    }

    @Test
    void matchRate_percentAndDivByZeroGuard() {
        assertThat(ConditionScannerService.matchRate(0, 5)).isEqualByComparingTo("0");
        assertThat(ConditionScannerService.matchRate(100, 5)).isEqualByComparingTo("5.00");
        assertThat(ConditionScannerService.matchRate(3, 1)).isEqualByComparingTo("33.33"); // HALF_UP
    }

    @Test
    void frequencyGrade_bands() {
        assertThat(ConditionScannerService.frequencyGrade(new BigDecimal("1.99"))).isEqualTo("LOW");
        assertThat(ConditionScannerService.frequencyGrade(new BigDecimal("2"))).isEqualTo("MEDIUM");
        assertThat(ConditionScannerService.frequencyGrade(new BigDecimal("9.99"))).isEqualTo("MEDIUM");
        assertThat(ConditionScannerService.frequencyGrade(BigDecimal.TEN)).isEqualTo("HIGH");
    }
}
