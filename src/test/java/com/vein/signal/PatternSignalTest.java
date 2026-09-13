package com.vein.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 신호 멱등키(§10.1) 생성 순수 로직 회귀 보호(R161). DB unique 제약과 중복 신호 방지의
 * 근간이므로 포맷·결정성·필드별 구분이 어긋나면 안 된다.
 */
class PatternSignalTest {

    private static final Instant ANCHOR = Instant.parse("2026-09-13T00:00:00Z");

    @Test
    void formatsPipeDelimitedKey() {
        String key = PatternSignal.buildSignalKey("v1", "abc-rule", 42L, "1d", ANCHOR);
        assertThat(key).isEqualTo("v1|abc-rule|42|1d|2026-09-13T00:00:00Z");
    }

    @Test
    void isDeterministicForSameInputs() {
        assertThat(PatternSignal.buildSignalKey("v1", "r", 1L, "4h", ANCHOR))
                .isEqualTo(PatternSignal.buildSignalKey("v1", "r", 1L, "4h", ANCHOR));
    }

    @Test
    void differsWhenAnyFieldDiffers() {
        String base = PatternSignal.buildSignalKey("v1", "r", 1L, "1d", ANCHOR);
        assertThat(base).isNotEqualTo(PatternSignal.buildSignalKey("v2", "r", 1L, "1d", ANCHOR));
        assertThat(base).isNotEqualTo(PatternSignal.buildSignalKey("v1", "r2", 1L, "1d", ANCHOR));
        assertThat(base).isNotEqualTo(PatternSignal.buildSignalKey("v1", "r", 2L, "1d", ANCHOR));
        assertThat(base).isNotEqualTo(PatternSignal.buildSignalKey("v1", "r", 1L, "4h", ANCHOR));
        assertThat(base).isNotEqualTo(
                PatternSignal.buildSignalKey("v1", "r", 1L, "1d", ANCHOR.plusSeconds(60)));
    }
}
