package com.vein.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vein.config.PublicRateLimitFilter.Rule;

/**
 * 공개 엔드포인트 레이트리밋 라우팅 순수 로직 회귀 보호(R175).
 * signup은 정확 매칭(시간당 10), /public/ 은 접두사 매칭(분당 60), 그 외는 미적용(null).
 */
class PublicRateLimitFilterTest {

    @Test
    void signupIsExactMatchWithHourlyCap() {
        Rule r = PublicRateLimitFilter.ruleFor("/api/v1/auth/signup");
        assertThat(r).isNotNull();
        assertThat(r.exact()).isTrue();
        assertThat(r.max()).isEqualTo(10);
    }

    @Test
    void signupExactnessRejectsSuffix() {
        // 정확 매칭이라 뒤에 문자가 붙으면 매칭 안 됨(접두사 규칙과도 무관)
        assertThat(PublicRateLimitFilter.ruleFor("/api/v1/auth/signupX")).isNull();
    }

    @Test
    void publicPrefixMatchesSubpathsWithPerMinuteCap() {
        Rule r = PublicRateLimitFilter.ruleFor("/api/v1/public/reports/weekly");
        assertThat(r).isNotNull();
        assertThat(r.exact()).isFalse();
        assertThat(r.max()).isEqualTo(60);
    }

    @Test
    void unmatchedAndNullReturnNull() {
        assertThat(PublicRateLimitFilter.ruleFor("/api/v1/signals")).isNull();
        assertThat(PublicRateLimitFilter.ruleFor(null)).isNull();
    }
}
