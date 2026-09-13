package com.vein.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 경제 캘린더 순수 헬퍼 회귀 보호(R133). NFP는 매월 첫째 금요일, dday는 today→event 일수.
 */
class EconomicCalendarProviderTest {

    @Test
    void firstFriday_returnsFirstFridayOfMonth() {
        // 2026-06-01 is a Monday -> first Friday is 2026-06-05
        LocalDate f = EconomicCalendarProvider.firstFriday(LocalDate.of(2026, 6, 1));
        assertThat(f.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(f).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    void firstFriday_whenFirstIsFriday_returnsThatDay() {
        // 2026-05-01 is a Friday -> shift 0
        assertThat(EconomicCalendarProvider.firstFriday(LocalDate.of(2026, 5, 1)))
                .isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void firstFriday_alwaysWithinFirstSevenDays() {
        for (int m = 1; m <= 12; m++) {
            LocalDate f = EconomicCalendarProvider.firstFriday(LocalDate.of(2026, m, 1));
            assertThat(f.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
            assertThat(f.getDayOfMonth()).isBetween(1, 7);
        }
    }

    @Test
    void dday_isSignedDayDifference() {
        LocalDate today = LocalDate.of(2026, 6, 12);
        assertThat(EconomicCalendarProvider.dday(today, today)).isEqualTo(0);
        assertThat(EconomicCalendarProvider.dday(today, today.plusDays(3))).isEqualTo(3);
        assertThat(EconomicCalendarProvider.dday(today, today.minusDays(2))).isEqualTo(-2);
    }
}
