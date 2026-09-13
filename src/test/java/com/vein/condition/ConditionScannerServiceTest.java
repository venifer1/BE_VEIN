package com.vein.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.common.Timeframe;
import com.vein.market.candle.Candle;

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

    // --- volumeRatio: 마지막(최신) 거래량 / 직전 최대 20봉 평균 (R158) ---
    private static Candle vol(String v) {
        return Candle.of(1L, Timeframe.D1, Instant.EPOCH, "test",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                v == null ? null : new BigDecimal(v), true);
    }

    @Test
    void volumeRatio_currentOverPrevAverage() {
        // 직전 3봉 평균 100, 현재 200 → 2.0000
        List<Candle> asc = List.of(vol("100"), vol("100"), vol("100"), vol("200"));
        assertThat(ConditionScannerService.volumeRatio(asc)).isEqualByComparingTo("2.0000");
    }

    @Test
    void volumeRatio_nullWhenCurrentNullOrNoPrevious() {
        assertThat(ConditionScannerService.volumeRatio(List.of(vol("100"), vol(null)))).isNull();
        // 직전 봉이 없으면(단일 봉) 평균 낼 표본 0 → null
        assertThat(ConditionScannerService.volumeRatio(List.of(vol("100")))).isNull();
    }

    @Test
    void volumeRatio_windowCappedAt20PreviousBars() {
        // index0(거대값)은 20봉 창 밖 → 제외. index1..20=100(20봉) 평균 100, 현재=300 → 3.0000
        List<Candle> asc = new ArrayList<>();
        asc.add(vol("999999")); // index 0 (창 밖)
        for (int i = 0; i < 20; i++) asc.add(vol("100")); // index 1..20
        asc.add(vol("300")); // 현재
        assertThat(ConditionScannerService.volumeRatio(asc)).isEqualByComparingTo("3.0000");
    }

    @Test
    void volumeRatio_roundsToFourDecimals() {
        // 현재 100 / 평균 3 = 33.3333
        assertThat(ConditionScannerService.volumeRatio(List.of(vol("3"), vol("3"), vol("3"), vol("100"))))
                .isEqualByComparingTo("33.3333");
    }
}
