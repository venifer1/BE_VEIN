package com.vein.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 주식 심볼 거래소 접미사 제거(예: 005930.KS → 005930) 순수 로직 회귀 보호(R179).
 * 첫 점(.)에서 자른다.
 */
class EquityInstrumentSyncServiceTest {

    @Test
    void stripsExchangeSuffix() {
        assertThat(EquityInstrumentSyncService.stripSuffix("005930.KS")).isEqualTo("005930");
        assertThat(EquityInstrumentSyncService.stripSuffix("035420.KQ")).isEqualTo("035420");
    }

    @Test
    void leavesSymbolWithoutDotUnchanged() {
        assertThat(EquityInstrumentSyncService.stripSuffix("AAPL")).isEqualTo("AAPL");
        assertThat(EquityInstrumentSyncService.stripSuffix("")).isEqualTo("");
    }

    @Test
    void cutsAtFirstDot() {
        // 첫 점에서 자르므로 티커에 점이 있으면 뒤가 잘림(BRK.B → BRK)
        assertThat(EquityInstrumentSyncService.stripSuffix("BRK.B")).isEqualTo("BRK");
        // 선행 점이면 빈 문자열
        assertThat(EquityInstrumentSyncService.stripSuffix(".KS")).isEqualTo("");
    }
}
