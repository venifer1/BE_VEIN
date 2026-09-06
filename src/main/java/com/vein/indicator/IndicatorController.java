package com.vein.indicator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.indicator.IndicatorService.IndicatorSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/instruments/{id}/indicators")
@Tag(name = "Indicators", description = "Technical indicator summary per instrument & timeframe")
public class IndicatorController {

    private final IndicatorService indicatorService;

    public IndicatorController(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    @GetMapping
    @Operation(summary = "Indicator summary",
            description = "RSI(14), MA(5/20/60/120), Bollinger(20,2), MACD(12,26,9) "
                    + "computed from stored candles.")
    public ApiResponse<IndicatorSummary> indicators(
            @PathVariable Long id,
            @RequestParam String timeframe) {
        return ApiResponse.of(indicatorService.compute(id, timeframe));
    }
}
