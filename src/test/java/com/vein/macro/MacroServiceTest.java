package com.vein.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.macro.MacroDto.Signal;

/**
 * 시장 국면 판정 로직(R37) 회귀 보호(R132). 순수 함수 {@code label}:
 * score≥2 BULL · score≤-2 BEAR · 그 외 (강세·약세 신호 공존) TRANSITION · 아니면 RANGE ·
 * 신호가 비면 RANGE.
 */
class MacroServiceTest {

    private static Signal bull() {
        return new Signal("X", "BULLISH", "d");
    }

    private static Signal bear() {
        return new Signal("Y", "BEARISH", "d");
    }

    @Test
    void score2OrMore_isBull() {
        assertThat(MacroService.label(2, List.of(bull())).label()).isEqualTo("BULL");
        assertThat(MacroService.label(5, List.of(bull(), bull())).label()).isEqualTo("BULL");
    }

    @Test
    void scoreMinus2OrLess_isBear() {
        assertThat(MacroService.label(-2, List.of(bear())).label()).isEqualTo("BEAR");
        assertThat(MacroService.label(-4, List.of(bear())).label()).isEqualTo("BEAR");
    }

    @Test
    void mixedSignalsWithinBand_isTransition() {
        assertThat(MacroService.label(0, List.of(bull(), bear())).label()).isEqualTo("TRANSITION");
        assertThat(MacroService.label(1, List.of(bull(), bear())).label()).isEqualTo("TRANSITION");
    }

    @Test
    void oneSidedWithinBand_isRange() {
        // score 1, only bullish (no opposing bear) -> not enough to be BULL, not mixed -> RANGE
        assertThat(MacroService.label(1, List.of(bull())).label()).isEqualTo("RANGE");
    }

    @Test
    void emptySignals_isRange_regardlessOfScore() {
        assertThat(MacroService.label(5, List.of()).label()).isEqualTo("RANGE");
        assertThat(MacroService.label(-5, List.of()).label()).isEqualTo("RANGE");
    }

    @Test
    void preservesScoreAndSignals() {
        var r = MacroService.label(3, List.of(bull()));
        assertThat(r.score()).isEqualTo(3);
        assertThat(r.signals()).hasSize(1);
        assertThat(r.summary()).isNotBlank();
    }

    // --- 지표 표기 순수 헬퍼 (R198) ---

    @Test
    void parse_safelyHandlesNullBlankAndBadInput() {
        assertThat(MacroService.parse(null)).isNull();
        assertThat(MacroService.parse("  ")).isNull();
        assertThat(MacroService.parse("abc")).isNull();
        assertThat(MacroService.parse("3.5")).isEqualByComparingTo("3.5");
        assertThat(MacroService.parse("-2")).isEqualByComparingTo("-2");
    }

    @Test
    void plain_isNullSafePlainString() {
        assertThat(MacroService.plain(null)).isNull();
        assertThat(MacroService.plain(new BigDecimal("1E-8"))).isEqualTo("0.00000001");
    }

    @Test
    void signed_prependsPlusForNonNegative() {
        assertThat(MacroService.signed(new BigDecimal("1.5"))).isEqualTo("+1.5");
        assertThat(MacroService.signed(BigDecimal.ZERO)).isEqualTo("+0");
        assertThat(MacroService.signed(new BigDecimal("-3"))).isEqualTo("-3");
    }
}
