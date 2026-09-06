package com.vein.macro;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
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

    public MacroController(MacroService macroService) {
        this.macroService = macroService;
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
}
