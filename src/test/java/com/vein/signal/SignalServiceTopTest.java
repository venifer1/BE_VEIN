package com.vein.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * R81: 홈 "오늘의 주목 신호"(/signals/top)가 같은 종목의 여러 신호(타임프레임만 다른)로 상위를
 * 채우던 것을 종목별 최상위 1건으로 dedup한다. 순수 함수 {@code dedupeByInstrument} 검증.
 */
class SignalServiceTopTest {

    private static PatternSignal sig(long id, long instrumentId) {
        return PatternSignal.builder().id(id).instrumentId(instrumentId).build();
    }

    @Test
    void keepsFirstPerInstrument_andLimits() {
        // 우선순위 정렬됐다고 가정: ins1이 두 번(1w,3d), ins2가 두 번…
        List<PatternSignal> ordered = List.of(
                sig(1, 100), sig(2, 100), sig(3, 200), sig(4, 200), sig(5, 300), sig(6, 400));

        List<PatternSignal> top3 = SignalService.dedupeByInstrument(ordered, 3);

        assertThat(top3).extracting(PatternSignal::getId).containsExactly(1L, 3L, 5L);
        assertThat(top3).extracting(PatternSignal::getInstrumentId).containsExactly(100L, 200L, 300L);
    }

    @Test
    void returnsAllDistinct_whenLimitExceedsDistinctCount() {
        List<PatternSignal> ordered = List.of(sig(1, 100), sig(2, 100), sig(3, 200));

        List<PatternSignal> result = SignalService.dedupeByInstrument(ordered, 10);

        assertThat(result).extracting(PatternSignal::getInstrumentId).containsExactly(100L, 200L);
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(SignalService.dedupeByInstrument(List.of(), 5)).isEmpty();
    }
}
