package com.vein.market.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.vein.market.index.MarketIndexService.HistoryPoint;

/**
 * 지수 히스토리 다운샘플링(균등 간격, 최신점 항상 포함) 순수 로직 회귀 보호(R169).
 */
class MarketIndexServiceTest {

    private static HistoryPoint hp(int i) {
        return new HistoryPoint("t" + i, String.valueOf(i));
    }

    private static List<HistoryPoint> series(int n) {
        return IntStream.range(0, n).mapToObj(MarketIndexServiceTest::hp).toList();
    }

    @Test
    void returnsInputWhenWithinMax() {
        List<HistoryPoint> in = series(3);
        assertThat(MarketIndexService.downsample(in, 5)).isSameAs(in);
        assertThat(MarketIndexService.downsample(in, 3)).isSameAs(in); // n==max
    }

    @Test
    void picksEvenlySpacedAndAlwaysKeepsLatest() {
        // n=10, max=5 → indices 0,2,4,6, + 마지막 9
        List<HistoryPoint> out = MarketIndexService.downsample(series(10), 5);
        assertThat(out).hasSize(5);
        assertThat(out.stream().map(HistoryPoint::value).toList())
                .containsExactly("0", "2", "4", "6", "9");
    }

    @Test
    void alwaysIncludesFirstAndLast() {
        List<HistoryPoint> out = MarketIndexService.downsample(series(100), 7);
        assertThat(out).hasSize(7);
        assertThat(out.get(0).value()).isEqualTo("0");
        assertThat(out.get(out.size() - 1).value()).isEqualTo("99");
    }

    @Test
    void maxTwoReturnsFirstAndLastOnly() {
        List<HistoryPoint> out = MarketIndexService.downsample(series(50), 2);
        assertThat(out.stream().map(HistoryPoint::value).toList()).containsExactly("0", "49");
    }
}
