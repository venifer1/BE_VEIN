package com.vein.market.kimchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 김치 프리미엄 계산 (업비트가 / (바이낸스가 × 환율) − 1) × 100 순수 로직 회귀 보호(R165).
 * R165에서 refresh 루프 인라인 계산을 순수 함수로 추출(동작 보존).
 */
class KimchiPremiumServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void positivePremium() {
        // 1400 / (1 × 1000) − 1 = 0.4 → 40.0000%
        assertThat(KimchiPremiumService.premiumPct(bd("1400"), bd("1"), bd("1000")))
                .isEqualByComparingTo("40.0000");
    }

    @Test
    void negativePremium_reverseKimchi() {
        assertThat(KimchiPremiumService.premiumPct(bd("900"), bd("1"), bd("1000")))
                .isEqualByComparingTo("-10.0000");
    }

    @Test
    void zeroWhenParityHolds() {
        assertThat(KimchiPremiumService.premiumPct(bd("1000"), bd("1"), bd("1000")))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void roundsToFourDecimals() {
        // 1 / (1 × 3) − 1 = -0.6666... → -66.6667%
        assertThat(KimchiPremiumService.premiumPct(bd("1"), bd("1"), bd("3")))
                .isEqualByComparingTo("-66.6667");
    }

    @Test
    void nullForInvalidInputs() {
        assertThat(KimchiPremiumService.premiumPct(null, bd("1"), bd("1000"))).isNull();
        assertThat(KimchiPremiumService.premiumPct(bd("1000"), null, bd("1000"))).isNull();
        assertThat(KimchiPremiumService.premiumPct(bd("1000"), bd("1"), null)).isNull();
        assertThat(KimchiPremiumService.premiumPct(bd("1000"), bd("0"), bd("1000"))).isNull();
        assertThat(KimchiPremiumService.premiumPct(bd("1000"), bd("-1"), bd("1000"))).isNull();
    }
}
