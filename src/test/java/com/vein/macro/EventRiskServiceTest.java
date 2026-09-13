package com.vein.macro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 이벤트 리스크 D-day 라벨 회귀 보호(R134). D-N=이벤트 N일 전, D+N=N일 후, D-DAY=당일.
 */
class EventRiskServiceTest {

    @Test
    void ddayLabel_formatsBeforeSameAndAfter() {
        assertThat(EventRiskService.ddayLabel(0)).isEqualTo("D-DAY");
        assertThat(EventRiskService.ddayLabel(7)).isEqualTo("D-7");
        assertThat(EventRiskService.ddayLabel(1)).isEqualTo("D-1");
        assertThat(EventRiskService.ddayLabel(-2)).isEqualTo("D+2");
    }
}
