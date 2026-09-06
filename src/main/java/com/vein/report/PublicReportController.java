package com.vein.report;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.report.WeeklyReportDto.Report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public (no-auth) content endpoints (MONETIZATION 단계2 ②). Serves ONLY the
 * aggregated weekly pattern-performance report — the "가공 통계" that is safe to
 * expose openly (no individual signals, instruments, or raw candles). Rate-limited
 * per client IP by {@link com.vein.config.PublicRateLimitFilter}.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "비로그인 공개 리포트 (가공 통계만)")
public class PublicReportController {

    private final WeeklyReportService reportService;

    public PublicReportController(WeeklyReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/weekly")
    @Operation(summary = "주간 패턴 성과 리포트 (JSON)",
            description = "누적 신호 성과에서 패턴×시장×봉 단위 적중률·평균/중앙 수익률을 집계한 공개 리포트. "
                    + "개별 신호·종목·원시 캔들은 포함하지 않는다. overall(전체 가중 집계)·rows(표본순)·"
                    + "highlights(표본 5+ 최고/최저)로 구성. ?window_days=1..365(기본 90), "
                    + "?horizon=1h|4h|1d|3d|7d(기본 1d).")
    public ApiResponse<Report> weekly(
            @Parameter(description = "집계 창(일). 기본 90, 최대 365")
            @RequestParam(value = "window_days", required = false) Integer windowDays,
            @Parameter(description = "청산 기준 horizon. 기본 1d")
            @RequestParam(value = "horizon", required = false) String horizon) {
        return ApiResponse.of(reportService.weekly(windowDays, horizon, Instant.now()));
    }

    @GetMapping(value = "/reports/weekly.md", produces = "text/markdown; charset=UTF-8")
    @Operation(summary = "주간 패턴 성과 리포트 (Markdown)",
            description = "동일 리포트를 블로그·SNS에 바로 붙일 수 있는 마크다운 본문으로 반환.")
    public String weeklyMarkdown(
            @RequestParam(value = "window_days", required = false) Integer windowDays,
            @RequestParam(value = "horizon", required = false) String horizon) {
        return reportService.markdown(reportService.weekly(windowDays, horizon, Instant.now()));
    }
}
