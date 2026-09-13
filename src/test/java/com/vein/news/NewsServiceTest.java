package com.vein.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.vein.common.ApiException;

/**
 * 속보 조회 파라미터 정규화 순수 로직 회귀 보호(R164). source/symbol 필터 + 페이지 크기 클램프.
 */
class NewsServiceTest {

    @Test
    void normalizeSource_nullableAndValidated() {
        assertThat(NewsService.normalizeSource(null)).isNull();
        assertThat(NewsService.normalizeSource("   ")).isNull();
        assertThat(NewsService.normalizeSource(" telegram ")).isEqualTo("TELEGRAM");
        assertThat(NewsService.normalizeSource("bloomberg")).isEqualTo("BLOOMBERG");
        assertThatThrownBy(() -> NewsService.normalizeSource("cnn")).isInstanceOf(ApiException.class);
    }

    @Test
    void normalizeSymbol_nullableUppercasedTrimmed() {
        assertThat(NewsService.normalizeSymbol(null)).isNull();
        assertThat(NewsService.normalizeSymbol("  ")).isNull();
        assertThat(NewsService.normalizeSymbol(" krw-btc ")).isEqualTo("KRW-BTC");
    }

    @Test
    void clampSize_defaultsAndBounds() {
        assertThat(NewsService.clampSize(null)).isEqualTo(20); // 기본
        assertThat(NewsService.clampSize(5)).isEqualTo(5);
        assertThat(NewsService.clampSize(0)).isEqualTo(1); // 하한
        assertThat(NewsService.clampSize(-10)).isEqualTo(1);
        assertThat(NewsService.clampSize(100)).isEqualTo(100); // 상한 경계
        assertThat(NewsService.clampSize(1000)).isEqualTo(100); // 상한 초과 → 100
    }
}
