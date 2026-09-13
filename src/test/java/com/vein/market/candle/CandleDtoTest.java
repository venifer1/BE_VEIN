package com.vein.market.candle;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.vein.common.TimeUtil;
import com.vein.common.Timeframe;

/**
 * 공개 캔들 DTO 매핑(BigDecimal→plain string, null 안전; 시각 ISO-8601 UTC) 회귀 보호(R162).
 */
class CandleDtoTest {

    private static final Instant OPEN = Instant.parse("2026-09-13T00:00:00Z");

    private static Candle candle(String open, String high, String low, String close, String volume) {
        return Candle.of(1L, Timeframe.D1, OPEN, "test",
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close),
                volume == null ? null : new BigDecimal(volume), true);
    }

    @Test
    void mapsAllFieldsAsPlainStrings() {
        CandleDto dto = CandleDto.from(candle("1.5", "2", "1", "1.75", "1000"));
        assertThat(dto.open()).isEqualTo("1.5");
        assertThat(dto.high()).isEqualTo("2");
        assertThat(dto.low()).isEqualTo("1");
        assertThat(dto.close()).isEqualTo("1.75");
        assertThat(dto.volume()).isEqualTo("1000");
        assertThat(dto.openTime()).isEqualTo(TimeUtil.toIso(OPEN));
    }

    @Test
    void usesPlainStringNotScientificNotation() {
        // toPlainString: 지수표기(1E-8) 대신 0.00000001
        CandleDto dto = CandleDto.from(candle("0.00000001", "0.00000001", "0.00000001", "0.00000001", "0"));
        assertThat(dto.close()).isEqualTo("0.00000001");
    }

    @Test
    void nullVolumeStaysNull() {
        CandleDto dto = CandleDto.from(candle("1", "1", "1", "1", null));
        assertThat(dto.volume()).isNull();
    }
}
