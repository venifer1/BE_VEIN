package com.vein.macro;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vein.macro.MacroDto.EconomicEvent;
import com.vein.macro.MacroDto.EventItem;
import com.vein.macro.MacroDto.EventRisk;

/**
 * 신호/종목의 <b>이벤트 리스크</b> 평가 (기획서 §15.1 후반부). 이벤트 전후에는 변동성이
 * 커져 기술적 패턴의 신뢰도가 떨어지므로, 신호를 볼 때 "지금 이 종목/시장에 임박한 고위험
 * 이벤트가 있는가"를 라벨로 붙인다.
 *
 * <p>두 축을 본다:
 * <ul>
 *   <li><b>매크로 이벤트</b>(FOMC·CPI·고용) — 위험자산 전반에 영향하므로 코인 포함 전 시장에
 *       적용. 오늘 기준 D-1~D+1이면 HIGH, 그 밖의 창은 MEDIUM.</li>
 *   <li><b>실적발표</b>(주식만) — 해당 종목 한정. D-7~D-day 창에서 임박도에 따라 HIGH/MEDIUM.</li>
 * </ul>
 * 저장된 점수를 바꾸지 않고 {@code confidenceDelta}(참고용 음수)와 사람이 읽는 note만 준다.
 * 임박 이벤트가 없으면 null을 반환해 UI가 조용히 숨긴다. 절대 throw하지 않는다.
 */
@Service
public class EventRiskService {

    private static final int MACRO_BACK = 1;    // 이벤트 당일/직후 D+... 소음까지 본다
    private static final int MACRO_FWD = 3;
    private static final int EARN_FWD = 7;      // D-7 ~ D-day
    private static final int HIGH_DELTA = -10;
    private static final int MEDIUM_DELTA = -5;

    private final EconomicCalendarProvider calendar;
    private final EarningsProvider earnings;

    public EventRiskService(EconomicCalendarProvider calendar, EarningsProvider earnings) {
        this.calendar = calendar;
        this.earnings = earnings;
    }

    /** 종목(market/symbol)에 대한 이벤트 리스크. 임박 이벤트 없으면 null. */
    public EventRisk assess(String market, String symbol) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<EventItem> hits = new ArrayList<>();

        // --- 매크로 이벤트: 전 시장 공통 ---
        for (EconomicEvent e : calendar.window(today, MACRO_BACK, MACRO_FWD)) {
            hits.add(new EventItem(e.date(), e.dday(), e.type(), e.title()));
        }

        // --- 실적발표: 해당 주식 종목 한정 ---
        LocalDate earn = earnings.nextEarnings(market, symbol);
        if (earn != null) {
            int dday = (int) ChronoUnit.DAYS.between(today, earn);
            if (dday >= 0 && dday <= EARN_FWD) {
                hits.add(new EventItem(earn.toString(), dday, "EARNINGS",
                        (symbol == null ? "" : symbol + " ") + "실적발표"));
            }
        }

        if (hits.isEmpty()) {
            return null;
        }
        // 가장 임박한(|D-day| 최소) 항목이 레벨을 결정: D-1~D+1이면 HIGH.
        int minAbs = hits.stream().mapToInt(h -> Math.abs(h.dday())).min().orElse(99);
        boolean high = minAbs <= 1;
        String level = high ? "HIGH" : "MEDIUM";
        int delta = high ? HIGH_DELTA : MEDIUM_DELTA;

        EventItem nearest = hits.stream()
                .min((a, b) -> Integer.compare(Math.abs(a.dday()), Math.abs(b.dday())))
                .orElse(hits.get(0));
        String note = ddayLabel(nearest.dday()) + " " + nearest.title()
                + " — 이벤트 전후 변동성 확대, 신호 신뢰도 하향 참고(" + delta + ")";

        return new EventRisk(true, level, delta, note, hits);
    }

    // package-private for unit testing (R134). D-N=이벤트 N일 전, D+N=N일 후, D-DAY=당일.
    static String ddayLabel(int dday) {
        if (dday == 0) {
            return "D-DAY";
        }
        return dday > 0 ? "D-" + dday : "D+" + (-dday);
    }
}
