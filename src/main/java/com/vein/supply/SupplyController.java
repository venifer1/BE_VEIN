package com.vein.supply;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.common.Freshness;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Coin supply endpoint (API_CONTRACT §6): CoinGecko circulating supply / FDV.
 */
@RestController
@RequestMapping("/api/v1/supply")
@Tag(name = "Supply", description = "CoinGecko circulating supply / FDV")
public class SupplyController {

    /** 60s refresh; consider stale at > 5 min (429 backoff can stretch this). */
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(5);

    private final SupplyService supplyService;

    public SupplyController(SupplyService supplyService) {
        this.supplyService = supplyService;
    }

    @GetMapping
    @Operation(summary = "Supply list",
            description = "?sort=market_cap|price|circulating_pct|fdv|rank & q=. Columns: rank, "
                    + "name, symbol, price_usd, market_cap, circulating, total, max, circulating_pct, fdv.")
    public ApiResponse<List<SupplyDto>> supply(@RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String q) {
        SupplyService.validateSort(sort);
        List<SupplyDto> data = supplyService.list(sort, q);
        return ApiResponse.of(data, freshness(supplyService.lastCollectedAt()).name());
    }

    private static Freshness freshness(Instant collectedAt) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(FRESH_WINDOW) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
