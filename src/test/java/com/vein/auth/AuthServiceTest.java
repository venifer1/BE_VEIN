package com.vein.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 가입 메타데이터 정리 헬퍼 trimTo 순수 로직 회귀 보호(R183).
 * strip 후 빈 값이면 null, max 초과면 잘라냄(유입추적 컬럼 길이 방어).
 */
class AuthServiceTest {

    @Test
    void nullAndBlankBecomeNull() {
        assertThat(AuthService.trimTo(null, 10)).isNull();
        assertThat(AuthService.trimTo("   ", 10)).isNull();
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(AuthService.trimTo("  hello  ", 10)).isEqualTo("hello");
    }

    @Test
    void truncatesToMax() {
        assertThat(AuthService.trimTo("abcdefghij", 5)).isEqualTo("abcde");
        assertThat(AuthService.trimTo("abc", 5)).isEqualTo("abc"); // 이하면 그대로
        // strip 먼저, 그다음 길이 제한
        assertThat(AuthService.trimTo("  abcdefg  ", 3)).isEqualTo("abc");
    }
}
