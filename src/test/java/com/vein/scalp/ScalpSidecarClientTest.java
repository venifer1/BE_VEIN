package com.vein.scalp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 틱띄기(스캘핑) 매수/매도 비율 표기 순수 로직 회귀 보호(R155).
 * ratio = part/total, 소수 4자리 HALF_UP. 호출부가 total==0을 미리 null 처리하므로
 * 여기서는 total>0 케이스만 검증.
 */
class ScalpSidecarClientTest {

    @Test
    void formatsRatioToFourDecimals() {
        assertThat(ScalpSidecarClient.ratio(1, 4)).isEqualTo("0.2500");
        assertThat(ScalpSidecarClient.ratio(3, 4)).isEqualTo("0.7500");
        assertThat(ScalpSidecarClient.ratio(5, 5)).isEqualTo("1.0000");
    }

    @Test
    void zeroPartYieldsZero() {
        assertThat(ScalpSidecarClient.ratio(0, 5)).isEqualTo("0.0000");
    }

    @Test
    void roundsHalfUpAtFourthDecimal() {
        assertThat(ScalpSidecarClient.ratio(1, 3)).isEqualTo("0.3333"); // 0.33333 → 0.3333
        assertThat(ScalpSidecarClient.ratio(2, 3)).isEqualTo("0.6667"); // 0.66666 → 0.6667
    }

    // --- 체결 방향 분류(대소문자 무시 휴리스틱) (R171) ---

    @Test
    void isBuy_startsWithBorBid() {
        assertThat(ScalpSidecarClient.isBuy("buy")).isTrue();
        assertThat(ScalpSidecarClient.isBuy("BID")).isTrue();
        assertThat(ScalpSidecarClient.isBuy("bid")).isTrue();
        assertThat(ScalpSidecarClient.isBuy("sell")).isFalse();
        assertThat(ScalpSidecarClient.isBuy("ask")).isFalse();
    }

    @Test
    void isAsk_startsWithAorSorSell() {
        assertThat(ScalpSidecarClient.isAsk("ask")).isTrue(); // A로 시작
        assertThat(ScalpSidecarClient.isAsk("SELL")).isTrue();
        assertThat(ScalpSidecarClient.isAsk("sell")).isTrue(); // S로 시작
        assertThat(ScalpSidecarClient.isAsk("short")).isTrue(); // S로 시작
        assertThat(ScalpSidecarClient.isAsk("buy")).isFalse();
        assertThat(ScalpSidecarClient.isAsk("bid")).isFalse();
    }
}
