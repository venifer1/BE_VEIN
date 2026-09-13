package com.vein.explain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PatternScoreCalculatorTest {

    private final PatternScoreProperties weights =
            new PatternScoreProperties(30, 20, 20, 10, 20);

    @Test
    void strongInputsReachMaximumScore() {
        PatternScoreCalculator.Scores result = PatternScoreCalculator.calculate(
                new PatternScoreCalculator.Inputs(
                        BigDecimal.valueOf(100), BigDecimal.valueOf(2.5),
                        true, true, true, BigDecimal.valueOf(50),
                        BigDecimal.valueOf(3), 2, 0),
                weights);

        assertThat(result.total()).isEqualTo(100);
    }

    @Test
    void missingInputsStayNeutralRatherThanInflatingScore() {
        PatternScoreCalculator.Scores result = PatternScoreCalculator.calculate(
                new PatternScoreCalculator.Inputs(
                        null, null, false, false, false, null, null, 0, 0),
                weights);

        assertThat(result.total()).isEqualTo(40);
        assertThat(result.completion()).isEqualTo(15);
        assertThat(result.news()).isEqualTo(10);
    }

    @Test
    void adverseInputsAreClampedAtZero() {
        PatternScoreCalculator.Scores result = PatternScoreCalculator.calculate(
                new PatternScoreCalculator.Inputs(
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        false, false, false, BigDecimal.valueOf(90),
                        BigDecimal.valueOf(20), 0, 10),
                weights);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.news()).isZero();
    }

    private PatternScoreCalculator.Inputs in(boolean p, boolean m5, boolean macd,
            BigDecimal rsi, BigDecimal range, int pos, int neg) {
        // detectorScore·volumeRatio null → 중립(다른 하위점수 격리용)
        return new PatternScoreCalculator.Inputs(null, null, p, m5, macd, rsi, range, pos, neg);
    }

    @Test
    void trendScore_scalesWithNumberOfBullishChecks() {
        // 4개 중 2개 충족(가격>MA20, MA5>MA20) → 추세 20*2/4 = 10
        assertThat(PatternScoreCalculator.calculate(
                in(true, true, false, null, null, 0, 0), weights).trend()).isEqualTo(10);
        // 0개 → 0
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, null, 0, 0), weights).trend()).isZero();
    }

    @Test
    void volatilityScore_bandedBySweetSpot() {
        // [1,6] 스윗스팟 → 만점 10
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, BigDecimal.valueOf(3), 0, 0), weights).volatility())
                .isEqualTo(10);
        // 인접 밴드([0.5,1)) → 0.6배 = 6
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, new BigDecimal("0.7"), 0, 0), weights).volatility())
                .isEqualTo(6);
        // 밴드 밖(20%) → 0.2배 = 2
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, BigDecimal.valueOf(20), 0, 0), weights).volatility())
                .isEqualTo(2);
    }

    @Test
    void newsScore_stepsFromNeutralAndClamps() {
        // 중립 10, step 5. +1 → 15, -1 → 5, +3 → 25 클램프 20
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, null, 1, 0), weights).news()).isEqualTo(15);
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, null, 0, 1), weights).news()).isEqualTo(5);
        assertThat(PatternScoreCalculator.calculate(
                in(false, false, false, null, null, 3, 0), weights).news()).isEqualTo(20);
    }
}
