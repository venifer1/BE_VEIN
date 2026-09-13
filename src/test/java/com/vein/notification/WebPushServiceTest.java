package com.vein.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 웹푸시 구독 필드 정리 순수 로직 회귀 보호(R184).
 * normalize: null→"" + trim(필수 필드). trimToNull: 빈 값→null + trim(선택 필드).
 */
class WebPushServiceTest {

    @Test
    void normalize_nullToEmptyAndTrim() {
        assertThat(WebPushService.normalize(null)).isEqualTo("");
        assertThat(WebPushService.normalize("")).isEqualTo("");
        assertThat(WebPushService.normalize("  abc  ")).isEqualTo("abc");
    }

    @Test
    void trimToNull_blankToNullAndTrim() {
        assertThat(WebPushService.trimToNull(null)).isNull();
        assertThat(WebPushService.trimToNull("   ")).isNull();
        assertThat(WebPushService.trimToNull("  xyz  ")).isEqualTo("xyz");
    }
}
