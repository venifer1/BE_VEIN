package com.vein.explain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.common.Timeframe;
import com.vein.market.candle.Candle;

/**
 * 평균 고저폭(변동성) 계산 (high-low)/close*100 평균 순수 로직 회귀 보호(R157).
 * 변동성 점수 근거로 쓰임. close=0/null 캔들은 제외, 유효 캔들 없으면 null.
 */
class SignalExplainServiceTest {

    private static Candle candle(String high, String low, String close) {
        return Candle.of(1L, Timeframe.D1, Instant.EPOCH, "test",
                new BigDecimal(close), // open (미사용)
                high == null ? null : new BigDecimal(high),
                low == null ? null : new BigDecimal(low),
                close == null ? null : new BigDecimal(close),
                BigDecimal.ONE, // volume (미사용)
                true);
    }

    @Test
    void averagesPerCandleRangePercent() {
        // A: 20/100=20%, B: 10/100=10% → 평균 15.00
        BigDecimal r = SignalExplainService.averageRangePct(List.of(
                candle("110", "90", "100"),
                candle("105", "95", "100")));
        assertThat(r).isEqualByComparingTo("15.00");
    }

    @Test
    void skipsCandlesWithZeroOrNullClose() {
        BigDecimal r = SignalExplainService.averageRangePct(List.of(
                candle("110", "90", "100"),
                candle("105", "95", "0"), // close=0 → 제외
                candle("105", "95", "100")));
        assertThat(r).isEqualByComparingTo("15.00");
    }

    @Test
    void returnsNullWhenNoUsableCandles() {
        assertThat(SignalExplainService.averageRangePct(List.of())).isNull();
        assertThat(SignalExplainService.averageRangePct(List.of(candle("1", "1", "0")))).isNull();
    }

    @Test
    void roundsAverageToTwoDecimals() {
        // 0.5/100.3*100 = 0.498504... → 0.50
        BigDecimal r = SignalExplainService.averageRangePct(List.of(candle("100.5", "100", "100.3")));
        assertThat(r).isEqualByComparingTo("0.50");
    }
}
