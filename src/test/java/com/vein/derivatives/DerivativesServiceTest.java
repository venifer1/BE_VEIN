package com.vein.derivatives;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 파생 목록 정렬 보조 순수 로직 회귀 보호(R166). 메이저 우선순위·plain 표기·안전 파싱.
 */
class DerivativesServiceTest {

    @Test
    void priorityIndex_majorsOrderedUnknownsLast() {
        assertThat(DerivativesService.priorityIndex("BTC")).isEqualTo(0);
        assertThat(DerivativesService.priorityIndex("ETH")).isEqualTo(1);
        assertThat(DerivativesService.priorityIndex("TIA")).isEqualTo(29);
        assertThat(DerivativesService.priorityIndex("WLD")).isEqualTo(Integer.MAX_VALUE);
        // 대문자 목록이라 소문자는 매칭 안 됨(뒤로)
        assertThat(DerivativesService.priorityIndex("btc")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void plain_nullSafeNoScientificNotation() {
        assertThat(DerivativesService.plain(null)).isNull();
        assertThat(DerivativesService.plain(new BigDecimal("1.50"))).isEqualTo("1.50");
        assertThat(DerivativesService.plain(new BigDecimal("1E-8"))).isEqualTo("0.00000001");
    }

    @Test
    void num_negativeInfinityForNullOrInvalid() {
        // 파생 정렬은 무효값을 맨 뒤로 보내기 위해 -inf 사용(김치 num의 0과 다름)
        assertThat(DerivativesService.num(null)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(DerivativesService.num("abc")).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(DerivativesService.num("3.5")).isEqualTo(3.5);
        assertThat(DerivativesService.num("-2")).isEqualTo(-2.0);
    }
}
