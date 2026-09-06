package com.vein.market.candle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.market.candle.CandleService.CandleResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/instruments/{id}/candles")
@Tag(name = "Candles", description = "OHLCV candles per instrument & timeframe")
public class CandleController {

    private final CandleService candleService;

    public CandleController(CandleService candleService) {
        this.candleService = candleService;
    }

    @GetMapping
    @Operation(summary = "Get candles",
            description = "Returns OHLCV candles for an instrument and timeframe. "
                    + "Freshness is reported in meta.")
    public ApiResponse<CandleResponse> getCandles(
            @PathVariable Long id,
            @RequestParam String timeframe,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit) {
        CandleResponse response = candleService.getCandles(id, timeframe, from, to, limit);
        return ApiResponse.of(response, response.freshness().name());
    }
}
