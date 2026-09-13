package com.vein.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Authorization 헤더 → Bearer 토큰 추출 순수 로직 회귀 보호(R176).
 * R176에서 doFilterInternal 인라인 추출을 순수 함수로 분리(동작 보존).
 */
class JwtAuthFilterTest {

    @Test
    void extractsTokenAfterBearerPrefix() {
        assertThat(JwtAuthFilter.resolveBearerToken("Bearer abc123")).isEqualTo("abc123");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(JwtAuthFilter.resolveBearerToken("Bearer   abc  ")).isEqualTo("abc");
    }

    @Test
    void nullWhenMissingOrWrongScheme() {
        assertThat(JwtAuthFilter.resolveBearerToken(null)).isNull();
        assertThat(JwtAuthFilter.resolveBearerToken("Basic abc")).isNull();
        assertThat(JwtAuthFilter.resolveBearerToken("bearer abc")).isNull(); // 대소문자 구분
    }

    @Test
    void emptyStringWhenPrefixOnly() {
        // "Bearer " 만 있으면 빈 토큰(null 아님) → 이후 parse 단계에서 거부됨
        assertThat(JwtAuthFilter.resolveBearerToken("Bearer ")).isEqualTo("");
    }
}
