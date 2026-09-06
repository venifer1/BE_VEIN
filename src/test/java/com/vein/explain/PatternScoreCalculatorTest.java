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
}
