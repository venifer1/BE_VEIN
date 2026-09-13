package com.vein.macro;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vein.macro.MacroDto.EconomicEvent;

/**
 * 고위험 미국 매크로 이벤트 캘린더 (기획서 §15.1 후반부) — <b>keyless</b>.
 *
 * <p>이벤트 소스 전략(사이드카 스텁-폴백과 동일 철학, 외부 키·호출 없음):
 * <ul>
 *   <li><b>FOMC 결정일 · CPI 발표일</b> — 규칙으로 유도할 수 없어 <b>큐레이션 고정일</b>로 둔다
 *       (연 8회 FOMC는 불규칙, CPI는 익월 중순). 값은 사람이 갱신하는 상수 테이블.</li>
 *   <li><b>고용보고서(비농업, NFP)</b> — BLS 규칙상 <b>매월 첫째 금요일</b>이라 어떤 실행일에도
 *       정확히 생성된다.</li>
 * </ul>
 * FRED/외부 경제 캘린더 API 연동은 후속 과제. 고정일 테이블이 비어도(창 밖) 국면 판정 등
 * 다른 경로에 영향을 주지 않는다.
 */
@Component
public class EconomicCalendarProvider {

    private static final String US = "US";
    private static final String HIGH = "HIGH";

    /** 큐레이션 FOMC 결정일(회의 둘째 날). 갱신 필요 시 이 상수만 고친다. */
    private static final List<LocalDate> FOMC = List.of(
            LocalDate.of(2026, 1, 28), LocalDate.of(2026, 3, 18), LocalDate.of(2026, 4, 29),
            LocalDate.of(2026, 6, 17), LocalDate.of(2026, 7, 29), LocalDate.of(2026, 9, 16),
            LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 16),
            LocalDate.of(2027, 1, 27), LocalDate.of(2027, 3, 17), LocalDate.of(2027, 4, 28));

    /** 큐레이션 CPI 발표일(익월 지표, 미 노동통계국 발표). */
    private static final List<LocalDate> CPI = List.of(
            LocalDate.of(2026, 1, 13), LocalDate.of(2026, 2, 11), LocalDate.of(2026, 3, 11),
            LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 12), LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 10, 13), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 12, 10),
            LocalDate.of(2027, 1, 13), LocalDate.of(2027, 2, 10));

    /**
     * {@code [today-backDays, today+forwardDays]} 창의 고위험 이벤트를 오늘 근접 순(|D-day|)으로
     * 반환한다. {@code dday}는 오늘 기준 D±n. 절대 throw하지 않는다.
     */
    public List<EconomicEvent> window(LocalDate today, int backDays, int forwardDays) {
        LocalDate from = today.minusDays(Math.max(0, backDays));
        LocalDate to = today.plusDays(Math.max(0, forwardDays));

        List<EconomicEvent> out = new ArrayList<>();
        addFixed(out, FOMC, "FOMC", "미국 FOMC 금리결정", from, to, today);
        addFixed(out, CPI, "CPI", "미국 소비자물가(CPI)", from, to, today);
        addEmployment(out, from, to, today);

        out.sort(Comparator.comparingInt((EconomicEvent e) -> Math.abs(e.dday()))
                .thenComparing(EconomicEvent::date));
        return out;
    }

    private static void addFixed(List<EconomicEvent> out, List<LocalDate> dates, String type,
                                 String title, LocalDate from, LocalDate to, LocalDate today) {
        for (LocalDate d : dates) {
            if (!d.isBefore(from) && !d.isAfter(to)) {
                out.add(new EconomicEvent(d.toString(), dday(today, d), type, title, US, HIGH));
            }
        }
    }

    /** 고용보고서(비농업): 매월 첫째 금요일. 창에 걸치는 각 월에 대해 생성. */
    private static void addEmployment(List<EconomicEvent> out, LocalDate from, LocalDate to,
                                      LocalDate today) {
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);
        while (!cursor.isAfter(lastMonth)) {
            LocalDate nfp = firstFriday(cursor);
            if (!nfp.isBefore(from) && !nfp.isAfter(to)) {
                out.add(new EconomicEvent(nfp.toString(), dday(today, nfp), "EMPLOYMENT",
                        "미국 고용보고서(비농업)", US, HIGH));
            }
            cursor = cursor.plusMonths(1);
        }
    }

    // package-private for unit testing (R133) — 순수·결정적.
    static LocalDate firstFriday(LocalDate firstOfMonth) {
        int shift = (DayOfWeek.FRIDAY.getValue() - firstOfMonth.getDayOfWeek().getValue() + 7) % 7;
        return firstOfMonth.plusDays(shift);
    }

    static int dday(LocalDate today, LocalDate event) {
        return (int) ChronoUnit.DAYS.between(today, event);
    }
}
