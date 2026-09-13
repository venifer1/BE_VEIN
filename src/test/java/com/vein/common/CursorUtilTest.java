package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Round-trip + malformed-input tests for cursor pagination (R110). Cursors flow
 * from client back to {@code decode}; a bad one must surface as a clean
 * {@code INVALID_CURSOR} 400, never an uncaught 500.
 */
class CursorUtilTest {

    @Test
    void roundTrip_preservesTsMillisAndId() {
        Instant ts = Instant.ofEpochMilli(1_726_000_000_000L);
        CursorUtil.Decoded d = CursorUtil.decode(CursorUtil.encode(ts, 4242L));
        assertThat(d.ts()).isEqualTo(ts);
        assertThat(d.id()).isEqualTo(4242L);
    }

    @Test
    void encode_isUrlSafeBase64_noPadding() {
        String c = CursorUtil.encode(Instant.ofEpochMilli(1L), 1L);
        assertThat(c).doesNotContain("=", "+", "/");
    }

    @Test
    void decode_garbage_throwsInvalidCursor() {
        assertThatThrownBy(() -> CursorUtil.decode("!!!not-base64!!!"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_missingPipeSegment_throwsInvalidCursor() {
        // base64 of "onlyonepart" -> split has no id part
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("onlyonepart".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CursorUtil.decode(bad)).isInstanceOf(ApiException.class);
    }

    @Test
    void decode_nonNumericParts_throwsInvalidCursor() {
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("12|notanumber".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CursorUtil.decode(bad)).isInstanceOf(ApiException.class);
    }
}
