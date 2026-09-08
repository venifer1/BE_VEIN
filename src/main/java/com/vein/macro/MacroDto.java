package com.vein.macro;

import java.util.List;

/**
 * 거시경제 / 시장국면 스냅샷 (기획서 §15.1 · §39). 개별 신호를 볼 때마다 "지금 시장이
 * 어떤 국면인지"를 매번 따로 확인하던 것을 자동화한다.
 *
 * <p>국면(regime)은 <b>내부 데이터만으로 항상 판정</b>된다(이미 수집 중인 나스닥 지수
 * 추세 + 공포탐욕). 금리차·M2·달러인덱스는 <b>FRED 키가 있을 때만</b> 채워지고, 없으면
 * null로 두되 그 자체가 국면 판정을 막지는 않는다. 모든 값은 BigDecimal-as-String,
 * 시간은 UTC ISO-8601.
 */
public final class MacroDto {

    private MacroDto() {
    }

    /** {@code GET /api/v1/macro} body. Nullable blocks mean "해당 소스 미가용". */
    public record Snapshot(Regime regime, YieldCurve yieldCurve, MonetaryAggregate m2,
                           DollarIndex dxy, String generatedAt, List<String> sources) {
    }

    /**
     * 시장 국면 라벨과 근거. {@code label} ∈ BULL|BEAR|RANGE|TRANSITION.
     * {@code signals}는 어떤 신호가 어느 방향으로 기여했는지 사람이 읽는 설명.
     */
    public record Regime(String label, int score, String summary, List<Signal> signals) {
    }

    /** 국면 판정에 들어간 개별 신호 하나. {@code direction} ∈ BULLISH|BEARISH|NEUTRAL. */
    public record Signal(String key, String direction, String detail) {
    }

    /** 장단기 금리차 (FRED). 역전(10Y<2Y)은 경기침체 선행 신호. */
    public record YieldCurve(String tenor3m, String tenor2y, String tenor10y,
                             String spread10y2y, String spread10y3m, Boolean inverted, String asOf) {
    }

    /** M2 통화량 (FRED). 유동성 확장/수축 참고. */
    public record MonetaryAggregate(String value, String yoyPct, String asOf) {
    }

    /** 달러인덱스 (FRED broad USD index). 코인·미주·원화자산 해석 보조. */
    public record DollarIndex(String value, String trend, String asOf) {
    }

    /**
     * 경제 캘린더의 고위험 매크로 이벤트 하나 (기획서 §15.1 후반부).
     * {@code type} ∈ FOMC|CPI|EMPLOYMENT, {@code region} ∈ US|GLOBAL,
     * {@code impact} ∈ HIGH|MEDIUM. {@code dday}는 오늘 기준 D±n(양수=예정, 0=당일, 음수=경과).
     * FOMC·CPI는 큐레이션 고정일, 고용보고서(NFP)는 매월 첫째 금요일 규칙으로 생성한다.
     */
    public record EconomicEvent(String date, int dday, String type, String title,
                                String region, String impact) {
    }

    /** {@code GET /api/v1/macro/calendar} body. 오늘 근접 순으로 정렬된 이벤트 목록. */
    public record CalendarResponse(List<EconomicEvent> events, String generatedAt) {
    }

    /**
     * 특정 신호/종목에 대한 이벤트 리스크 라벨. 이벤트 전후에는 변동성이 커져 패턴 신뢰도가
     * 낮아지므로, 저장된 점수를 바꾸지 않고 <b>읽기 시점 힌트</b>로 노출한다.
     * {@code level} ∈ HIGH|MEDIUM, {@code confidenceDelta}는 참고용 점수 가감(음수).
     */
    public record EventRisk(boolean active, String level, int confidenceDelta,
                            String note, List<EventItem> events) {
    }

    /** EventRisk를 유발한 개별 이벤트. {@code dday}는 오늘 기준 D±n. */
    public record EventItem(String date, int dday, String type, String title) {
    }
}
