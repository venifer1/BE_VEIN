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
}
