package com.vein.tvl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.vein.common.ApiException;

/**
 * TVL 조회 모드 정규화·plain 표기 순수 로직 회귀 보호(R167).
 */
class TvlServiceTest {

    @Test
    void normalizeMode_defaultsProtocolAndValidates() {
        assertThat(TvlService.normalizeMode(null)).isEqualTo("PROTOCOL");
        assertThat(TvlService.normalizeMode("  ")).isEqualTo("PROTOCOL");
        assertThat(TvlService.normalizeMode(" chain ")).isEqualTo("CHAIN");
        assertThat(TvlService.normalizeMode("protocol")).isEqualTo("PROTOCOL");
        assertThatThrownBy(() -> TvlService.normalizeMode("POOL")).isInstanceOf(ApiException.class);
    }

    @Test
    void plain_nullSafeNoScientificNotation() {
        assertThat(TvlService.plain(null)).isNull();
        assertThat(TvlService.plain(new BigDecimal("1.50"))).isEqualTo("1.50");
        assertThat(TvlService.plain(new BigDecimal("1E9"))).isEqualTo("1000000000");
    }
}
