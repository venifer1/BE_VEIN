package com.vein.macro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.common.TimeUtil;
import com.vein.macro.MacroDto.CalendarResponse;
import com.vein.macro.MacroDto.Regime;
import com.vein.macro.MacroDto.Snapshot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 거시경제/시장국면 조회 (기획서 §15.1·§39). 인증 필요(인앱 기능).
 */
@RestController
@RequestMapping("/api/v1/macro")
@Tag(name = "Macro", description = "거시경제 지표 · 시장 국면(BULL/BEAR/RANGE/TRANSITION)")
public class MacroController {

    private final MacroService macroService;
    private final EconomicCalendarProvider calendar;

    public MacroController(MacroService macroService, EconomicCalendarProvider calendar) {
        this.macroService = macroService;
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "거시경제/시장국면 스냅샷",
            description = "국면 라벨(내부 나스닥 추세+공포탐욕으로 항상 판정) + 금리차·M2·달러인덱스"
                    + "(FRED 키 설정 시 채워짐). sources[]로 어떤 소스가 반영됐는지 노출. "
                    + "~10분 캐시, 상류 실패에도 마지막값 유지.")
    public ApiResponse<Snapshot> snapshot() {
        return ApiResponse.of(macroService.snapshot());
    }

    @GetMapping("/regime")
    @Operation(summary = "시장 국면만",
            description = "BULL|BEAR|RANGE|TRANSITION 라벨 + 점수 + 근거 신호 목록.")
    public ApiResponse<Regime> regime() {
        return ApiResponse.of(macroService.regime());
    }

    @GetMapping("/calendar")
    @Operation(summary = "경제 캘린더(고위험 매크로 이벤트)",
            description = "FOMC·CPI·고용보고서 등 위험자산 전반에 영향하는 미국 매크로 이벤트를 "
                    + "오늘 기준 D-day와 함께 반환. keyless(고정일 큐레이션+NFP 규칙 생성). "
                    + "days로 앞으로의 조회 창을 조절(기본 14, 최대 90).")
    public ApiResponse<CalendarResponse> calendar(
            @RequestParam(name = "days", defaultValue = "14") int days) {
        int fwd = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return ApiResponse.of(new CalendarResponse(
                calendar.window(today, 1, fwd),
                TimeUtil.toIso(Instant.now())));
    }
}
