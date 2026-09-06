package com.vein.derivatives;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.derivatives.LiquidationService.Snapshot;
import com.vein.derivatives.LiquidationService.Aggregation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/liquidations")
@Tag(name = "Liquidations", description = "Binance USD-M force-liquidation stream")
public class LiquidationController {

    private final LiquidationService service;

    public LiquidationController(LiquidationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Recent liquidation feed",
            description = "Recent Binance all-market force-order snapshots. SELL means a long "
                    + "position liquidation; BUY means a short position liquidation.")
    public ApiResponse<Snapshot> list(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(name = "min_notional", defaultValue = "10000") BigDecimal minNotional,
            @RequestParam(required = false) String symbol) {
        return ApiResponse.of(service.snapshot(limit, minNotional, symbol));
    }

    @GetMapping("/summary")
    @Operation(summary = "Liquidation aggregates and spike level",
            description = "Returns 1h/24h totals from the bounded in-memory feed and compares "
                    + "the latest five minutes with the prior 55-minute baseline.")
    public ApiResponse<Aggregation> summary(@RequestParam(required = false) String symbol) {
        return ApiResponse.of(service.aggregation(symbol));
    }
}
