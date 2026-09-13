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

    @Test
    void recoverHits_fromRoundedRate() {
        assertThat(WeeklyReportService.recoverHits("60", 10)).isEqualTo(6);
        assertThat(WeeklyReportService.recoverHits("61.9", 42)).isEqualTo(26); // 25.998 → 26
        assertThat(WeeklyReportService.recoverHits(null, 10)).isEqualTo(0);
        assertThat(WeeklyReportService.recoverHits("50", 0)).isEqualTo(0);
    }

    @Test
    void pct_weightedRecompute() {
        assertThat(WeeklyReportService.pct(6, 10)).isEqualTo("60.0");
        assertThat(WeeklyReportService.pct(1, 3)).isEqualTo("33.3");
        assertThat(WeeklyReportService.pct(0, 0)).isEqualTo("0.0");
    }

    @Test
    void signed_formatsWithSign() {
        assertThat(WeeklyReportService.signed("1.61")).isEqualTo("+1.61");
        assertThat(WeeklyReportService.signed("-0.42")).isEqualTo("-0.42");
        assertThat(WeeklyReportService.signed(null)).isEqualTo("0.00");
        assertThat(WeeklyReportService.signed("")).isEqualTo("0.00");
    }
}
