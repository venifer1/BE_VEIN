package com.vein.backtest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.backtest.BacktestDto.RunRequest;
import com.vein.backtest.BacktestDto.RunResponse;
import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Backtest engine endpoint (기획서 §11). Replays stored pattern signals as
 * trades and returns metrics, an equity curve and (capped) per-trade detail.
 * Computed on demand from stored signals + candles.
 */
@RestController
@RequestMapping("/api/v1/backtests")
@Tag(name = "Backtest", description = "Replay stored pattern signals as trades")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/run")
    @Operation(summary = "Run a backtest",
            description = "Replays signals of a given type (optionally scoped by market/timeframe) "
                    + "within a lookback window: entry at the detection price, exit at "
                    + "target/stop/horizon, compounded into an equity curve. Decimals are strings, "
                    + "times UTC.")
    public ApiResponse<RunResponse> run(@Valid @RequestBody RunRequest request) {
        return ApiResponse.of(backtestService.run(request));
    }
}
