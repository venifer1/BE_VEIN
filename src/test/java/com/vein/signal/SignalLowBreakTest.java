package com.vein.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 저가-이탈 무효화 완화 버퍼(R53) 순수 단위. 기준선 A=100 기준, 버퍼 3%면 임계선=97.
 */
class SignalLowBreakTest {

    private static final BigDecimal A = new BigDecimal("100");
    private static final BigDecimal BUF3 = new BigDecimal("0.03");

    @Test
    void shallowDipWithinBufferIsForgiven() {
        // 저가 98 (2% 이탈) < 임계선 97? 아니오 → 봐줌
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("98"), A, BUF3)).isFalse();
        // 저가 97 정확히 임계선 → 미만 아님 → 봐줌
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("97"), A, BUF3)).isFalse();
    }

    @Test
    void deepBreakBeyondBufferInvalidates() {
        // 저가 96 (4% 이탈) < 97 → 무효
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("96"), A, BUF3)).isTrue();
    }

    @Test
    void touchingAExactlyIsNotABreak() {
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("100"), A, BUF3)).isFalse();
    }

    @Test
    void zeroBufferRestoresStrictBelow() {
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("99.99"), A, BigDecimal.ZERO)).isTrue();
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("100"), A, BigDecimal.ZERO)).isFalse();
    }

    @Test
    void nullOrNegativeBufferIsSafe() {
        assertThat(SignalStatusTransitionService.breaches(null, A, BUF3)).isFalse();
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("96"), null, BUF3)).isFalse();
        // 음수 버퍼는 0으로 취급 → strict below
        assertThat(SignalStatusTransitionService.breaches(new BigDecimal("99"), A, new BigDecimal("-0.1"))).isTrue();
    }

    @Test
    void thresholdPriceIsLineMinusBuffer() {
        // 실질 무효화가 = 기준선 × (1 − buffer). FE가 이 값을 "실질 무효화가"로 노출(R77).
        assertThat(SignalStatusTransitionService.thresholdPrice(A, BUF3)).isEqualByComparingTo("97");
        // 버퍼 0이면 기준선 그대로
        assertThat(SignalStatusTransitionService.thresholdPrice(A, BigDecimal.ZERO)).isEqualByComparingTo("100");
        // null/음수 버퍼는 0으로 취급 → 기준선 그대로
        assertThat(SignalStatusTransitionService.thresholdPrice(A, null)).isEqualByComparingTo("100");
        assertThat(SignalStatusTransitionService.thresholdPrice(A, new BigDecimal("-0.1"))).isEqualByComparingTo("100");
        // R77 실측 예시(/signals/275): 89.25 × 0.97 = 86.5725 — 반올림 드리프트 고정
        assertThat(SignalStatusTransitionService.thresholdPrice(new BigDecimal("89.25"), BUF3))
                .isEqualByComparingTo("86.5725");
    }
}
