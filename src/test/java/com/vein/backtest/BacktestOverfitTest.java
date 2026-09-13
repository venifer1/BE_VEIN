package com.vein.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vein.backtest.BacktestDto.Metrics;

/**
 * 워크포워드 과적합 경고 규칙(R OOS, 기획서 §11) 회귀 보호(R139).
 * 규칙: (IS 평균수익>0 && OOS 평균수익<0) 또는 (OOS 승률 < IS 승률 − 15%p) → 과적합.
 * 한쪽이라도 거래 0건이면 비교 불가로 false.
 */
class BacktestOverfitTest {

    /** winRate·avgReturn만 의미 있고 나머지는 채움용. */
    private static Metrics m(int trades, String winRate, String avgReturn) {
        return new Metrics(trades, winRate, avgReturn, "0", "1", "0", "0", "0", 0, 0);
    }

    @Test
    void similarPerformance_notOverfit() {
        assertThat(BacktestService.isOverfit(m(20, "60", "2"), m(20, "58", "1.5"))).isFalse();
    }

    @Test
    void bigWinRateDrop_isOverfit() {
        // 60 → 44 = 16%p 하락 (>15) → 과적합
        assertThat(BacktestService.isOverfit(m(20, "60", "2"), m(20, "44", "1"))).isTrue();
    }

    @Test
    void exactly15ppDrop_notOverfit() {
        // 60 → 45 = 정확히 15%p (strict <이라 경고 아님)
        assertThat(BacktestService.isOverfit(m(20, "60", "2"), m(20, "45", "1"))).isFalse();
    }

    @Test
    void profitFlipsToLoss_isOverfit() {
        // IS 평균수익 +2, OOS 평균수익 -0.5 → 과적합(승률 무관)
        assertThat(BacktestService.isOverfit(m(20, "55", "2"), m(20, "55", "-0.5"))).isTrue();
    }

    @Test
    void zeroTradesEitherSide_notComparable() {
        assertThat(BacktestService.isOverfit(m(0, "0", "0"), m(20, "10", "-5"))).isFalse();
        assertThat(BacktestService.isOverfit(m(20, "60", "2"), m(0, "0", "0"))).isFalse();
    }
}
