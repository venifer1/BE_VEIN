package com.vein.funding;

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
 * Funding-arbitrage endpoint (API_CONTRACT §6): Upbit spot vs Bybit perp funding.
 */
@RestController
@RequestMapping("/api/v1/funding-arb")
@Tag(name = "Funding", description = "펀비차익 — Upbit spot vs Bybit perp funding")
public class FundingController {

    /** 5min refresh; consider stale at > 12 min. */
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(12);

    private final FundingService fundingService;

    public FundingController(FundingService fundingService) {
        this.fundingService = fundingService;
    }

    @GetMapping
    @Operation(summary = "Funding arbitrage",
            description = "?sort=funding_desc|funding_asc. Columns: symbol, funding_pct, "
                    + "upbit_price, bybit_price, next_funding_at, expected_1x_pct, expected_2x_pct. "
                    + "Fees: Upbit 0.05% / Bybit 0.055% roundtrip.")
    public ApiResponse<List<FundingDto>> fundingArb(@RequestParam(required = false) String sort) {
        List<FundingDto> data = fundingService.list(sort);
        return ApiResponse.of(data, freshness(fundingService.lastCollectedAt()).name());
    }

    private static Freshness freshness(Instant collectedAt) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(FRESH_WINDOW) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
