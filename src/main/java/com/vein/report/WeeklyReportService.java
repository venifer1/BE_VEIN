package com.vein.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vein.common.TimeUtil;
import com.vein.report.WeeklyReportDto.Highlight;
import com.vein.report.WeeklyReportDto.Overall;
import com.vein.report.WeeklyReportDto.PatternStat;
import com.vein.report.WeeklyReportDto.Report;
import com.vein.signal.SignalPerformanceDto.SummaryRow;
import com.vein.signal.SignalPerformanceService;

/**
 * Assembles the public weekly pattern-performance report from the accumulated
 * signal-performance aggregates. Pure read-through over
 * {@link SignalPerformanceService#summary} — no signal-level data ever leaves here,
 * only per (pattern × market × timeframe) statistics.
 *
 * <p>The "weekly" is a publication cadence, not the data window: each report is a
 * trailing-window snapshot (default 90 days) of everything measured so far, which
 * is what makes hit-rate numbers meaningful rather than one week of noise.
 */
@Service
public class WeeklyReportService {

    static final int DEFAULT_WINDOW_DAYS = 90;
    static final String DEFAULT_HORIZON = "1d";
    static final int MAX_WINDOW_DAYS = 365;

    /** Rows below this sample size are too noisy to headline; excluded from highlights. */
    private static final long MIN_HIGHLIGHT_SAMPLE = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DISCLAIMER =
            "투자 참고용 · 투자권유 아님. 종목 추천이 아니라 과거 패턴의 통계 요약입니다. "
            + "과거 성과가 미래 수익을 보장하지 않습니다.";

    private final SignalPerformanceService performanceService;

    public WeeklyReportService(SignalPerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    /**
     * Build the report for a trailing window ending at {@code now}. {@code windowDays}
     * null/≤0 falls back to {@link #DEFAULT_WINDOW_DAYS} and is capped at
     * {@link #MAX_WINDOW_DAYS}; {@code horizon} null/blank falls back to {@link #DEFAULT_HORIZON}.
     */
    public Report weekly(Integer windowDays, String horizon, Instant now) {
        int window = (windowDays == null || windowDays <= 0) ? DEFAULT_WINDOW_DAYS
                : Math.min(windowDays, MAX_WINDOW_DAYS);
        String h = (horizon == null || horizon.isBlank()) ? DEFAULT_HORIZON : horizon;
        Instant from = now.minus(window, ChronoUnit.DAYS);

        List<SummaryRow> summary = performanceService.summary(null, null, null, h, from, null, null);

        List<PatternStat> rows = new ArrayList<>(summary.size());
        long totalSample = 0;
        long totalHits = 0;
        BigDecimal weightedReturn = BigDecimal.ZERO;
        for (SummaryRow r : summary) {
            rows.add(new PatternStat(r.type(), r.market(), r.timeframe(), r.sampleSize(),
                    r.hitRate(), r.avgReturnPct(), r.medianReturnPct()));
            totalSample += r.sampleSize();
            totalHits += recoverHits(r.hitRate(), r.sampleSize());
            weightedReturn = weightedReturn.add(
                    parse(r.avgReturnPct()).multiply(BigDecimal.valueOf(r.sampleSize())));
        }
        rows.sort(Comparator.comparingLong(PatternStat::sampleSize).reversed());

        Overall overall = new Overall(totalSample, pct(totalHits, totalSample),
                totalSample == 0 ? "0.00"
                        : weightedReturn.divide(BigDecimal.valueOf(totalSample), 2, RoundingMode.HALF_UP)
                                .toPlainString());

        return new Report(TimeUtil.toIso(now), window, h, overall, rows, highlights(rows), DISCLAIMER);
    }

    /** Best and worst pattern rows by hit rate, restricted to rows with a meaningful sample. */
    // package-private for unit testing (R141) — 순수(표본 5+ 최고/최저 hit-rate 선정).
    static List<Highlight> highlights(List<PatternStat> rows) {
        List<PatternStat> eligible = rows.stream()
                .filter(r -> r.sampleSize() >= MIN_HIGHLIGHT_SAMPLE)
                .sorted(Comparator.comparing((PatternStat r) -> parse(r.hitRate())).reversed())
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        List<Highlight> out = new ArrayList<>(2);
        PatternStat best = eligible.get(0);
        out.add(highlight("BEST", "가장 잘 맞은 패턴", best));
        PatternStat worst = eligible.get(eligible.size() - 1);
        if (!worst.equals(best)) {
            out.add(highlight("WORST", "가장 안 맞은 패턴", worst));
        }
        return out;
    }

    private static Highlight highlight(String kind, String label, PatternStat r) {
        return new Highlight(kind, label, r.type(), r.market(), r.timeframe(),
                r.sampleSize(), r.hitRate(), r.avgReturnPct());
    }

    /**
     * Render the report as a self-contained Markdown document for pasting into a
     * blog/SNS post — the actual "콘텐츠 발행" surface.
     */
    public String markdown(Report report) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# VEIN 주간 패턴 성과 리포트\n\n");
        sb.append("> 생성 ").append(report.generatedAt())
                .append(" · 최근 ").append(report.windowDays()).append("일")
                .append(" · 청산기준 ").append(report.horizon()).append("\n>\n");
        sb.append("> ⚠️ ").append(report.disclaimer()).append("\n\n");

        Overall o = report.overall();
        sb.append("## 전체\n\n");
        sb.append("표본 ").append(o.sampleSize()).append("건 · 적중률 ").append(o.hitRate())
                .append("% · 평균수익 ").append(signed(o.avgReturnPct())).append("%\n\n");

        sb.append("## 패턴별 성과\n\n");
        if (report.rows().isEmpty()) {
            sb.append("_아직 집계할 표본이 없습니다._\n\n");
        } else {
            sb.append("| 패턴 | 시장 | 봉 | 표본 | 적중률 | 평균수익 | 중앙값 |\n");
            sb.append("|---|---|---|---:|---:|---:|---:|\n");
            for (PatternStat r : report.rows()) {
                sb.append("| ").append(r.type())
                        .append(" | ").append(r.market())
                        .append(" | ").append(r.timeframe())
                        .append(" | ").append(r.sampleSize())
                        .append(" | ").append(r.hitRate()).append('%')
                        .append(" | ").append(signed(r.avgReturnPct())).append('%')
                        .append(" | ").append(signed(r.medianReturnPct())).append('%')
                        .append(" |\n");
            }
            sb.append('\n');
        }

        if (!report.highlights().isEmpty()) {
            sb.append("## 이번 주 하이라이트\n\n");
            for (Highlight hl : report.highlights()) {
                String icon = "BEST".equals(hl.kind()) ? "🔥" : "🧊";
                sb.append("- ").append(icon).append(" **").append(hl.label()).append("** — ")
                        .append(hl.type()).append(" · ").append(hl.market()).append(" · ")
                        .append(hl.timeframe()).append(" · 적중률 ").append(hl.hitRate())
                        .append("% (표본 ").append(hl.sampleSize()).append(")\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Recover the positive-return count from a rounded "hit_rate" and sample size. (R142: pkg-private for tests) */
    static long recoverHits(String hitRate, long n) {
        if (hitRate == null || n == 0) {
            return 0;
        }
        return parse(hitRate).multiply(BigDecimal.valueOf(n))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue();
    }

    static String pct(long hits, long total) {
        if (total == 0) {
            return "0.0";
        }
        return BigDecimal.valueOf(hits).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString();
    }

    /** Human-facing signed percent rounded to 2 dp: "+1.61" / "-0.42" / "0.00" for null/blank. */
    static String signed(String v) {
        if (v == null || v.isBlank()) {
            return "0.00";
        }
        BigDecimal b = parse(v).setScale(2, RoundingMode.HALF_UP);
        return b.signum() >= 0 ? "+" + b.toPlainString() : b.toPlainString();
    }

    private static BigDecimal parse(String s) {
        if (s == null || s.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
