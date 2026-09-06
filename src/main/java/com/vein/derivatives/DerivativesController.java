package com.vein.derivatives;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;
import com.vein.common.Freshness;
import com.vein.derivatives.DerivativesService.Detail;
import com.vein.derivatives.DerivativesService.Row;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Derivatives endpoints: keyless Binance USD-M perp mark price, funding, open
 * interest, OI value and long/short ratio. Live data with a ~60s cache; freshness
 * reported in {@code meta}. Never 500s on upstream failure (empty data instead).
 */
@RestController
@RequestMapping("/api/v1/derivatives")
@Tag(name = "Derivatives", description = "Binance USD-M perp funding / OI / long-short")
public class DerivativesController {

    /** Cache is ~60s; consider stale at > 3 min. */
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(3);

    private final DerivativesService service;

    public DerivativesController(DerivativesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Derivatives list",
            description = "Binance USD-M perps for the coins we track, sorted by OI value (USD) "
                    + "desc. ?limit=1..50 (default 20). mark_price/funding via /fapi/v1/premiumIndex, "
                    + "open_interest via /fapi/v1/openInterest, long_short_ratio via "
                    + "globalLongShortAccountRatio. Cached ~60s; empty data on upstream failure.")
    public ApiResponse<List<Row>> list(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        List<Row> data = service.list(limit);
        String freshness = freshness(service.lastCollectedAt()).name();
        return ApiResponse.of(data, freshness);
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Derivatives detail",
            description = "The row for one base symbol (e.g. BTC) plus long_short_history "
                    + "(1h x 24) and oi_history (1h x 24). 404 if the symbol is not tracked.")
    public ApiResponse<Detail> detail(@PathVariable String symbol) {
        Detail data = service.detail(symbol);
        if (data == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Derivatives symbol not found: " + symbol);
        }
        String freshness = freshness(service.lastCollectedAt()).name();
        return ApiResponse.of(data, freshness);
    }

    private static Freshness freshness(Instant collectedAt) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(FRESH_WINDOW) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
