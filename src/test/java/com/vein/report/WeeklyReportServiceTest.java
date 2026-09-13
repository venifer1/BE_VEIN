package com.vein.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.report.WeeklyReportDto.Highlight;
import com.vein.report.WeeklyReportDto.PatternStat;

/**
 * 주간 리포트 하이라이트 선정(R34 해자) 회귀 보호(R141). 표본 5건 미만은 제외,
 * 남은 것 중 hit-rate 최고=BEST·최저=WORST. 자격 1개면 BEST만, 0개면 빈 목록.
 */
class WeeklyReportServiceTest {

    private static PatternStat row(String type, long sample, String hitRate) {
        return new PatternStat(type, "CRYPTO", "1d", sample, hitRate, "1.0", "0.5");
    }

    @Test
    void picksBestAndWorstByHitRate_excludingLowSample() {
        List<Highlight> hi = WeeklyReportService.highlights(List.of(
                row("ABC", 40, "62"),
                row("TOP", 30, "48"),
                row("IMALOL", 20, "70"),
                row("ABC", 3, "99") // 표본<5 → 제외
        ));
        assertThat(hi).hasSize(2);
        assertThat(hi.get(0).kind()).isEqualTo("BEST");
        assertThat(hi.get(0).hitRate()).isEqualTo("70"); // IMALOL, 저표본 99 제외
        assertThat(hi.get(1).kind()).isEqualTo("WORST");
        assertThat(hi.get(1).hitRate()).isEqualTo("48");
    }

    @Test
    void singleEligible_onlyBest() {
        List<Highlight> hi = WeeklyReportService.highlights(List.of(
                row("ABC", 10, "55"),
                row("TOP", 2, "80") // 저표본 제외
        ));
        assertThat(hi).hasSize(1);
        assertThat(hi.get(0).kind()).isEqualTo("BEST");
    }

    @Test
    void noEligible_empty() {
        assertThat(WeeklyReportService.highlights(List.of(row("ABC", 4, "90")))).isEmpty();
        assertThat(WeeklyReportService.highlights(List.of())).isEmpty();
    }
}
