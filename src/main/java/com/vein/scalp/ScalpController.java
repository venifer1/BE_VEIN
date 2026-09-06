package com.vein.scalp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.common.Freshness;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Scalping (틱띄기) endpoints (API_CONTRACT §4). Server collects Upbit
 * microstructure (REST approximation) and serves the cached ranking / detail.
 */
@RestController
@RequestMapping("/api/v1/scalp")
@Tag(name = "Scalp", description = "틱띄기 — Upbit scalping ranking")
public class ScalpController {

    /** ~1s collection cadence; consider stale at > 30s. */
    private static final Duration FRESH_WINDOW = Duration.ofSeconds(30);

    private final ScalpService scalpService;

    public ScalpController(ScalpService scalpService) {
        this.scalpService = scalpService;
    }

    @GetMapping("/ranking")
    @Operation(summary = "Scalp ranking",
            description = "?limit=. Top KRW markets by scalping score (spread/TPS/micro-vol/imbalance).")
    public ApiResponse<List<ScalpDto.RankingRow>> ranking(
            @RequestParam(defaultValue = "25") int limit) {
        List<ScalpDto.RankingRow> data = scalpService.ranking(limit);
        return ApiResponse.of(data, freshness(scalpService.lastCollectedAt()).name());
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Scalp detail",
            description = "Recent buy/sell flow, top orderbook levels, wall/cancel-suspicion state.")
    public ApiResponse<ScalpDto.Detail> detail(@PathVariable String symbol) {
        return ApiResponse.of(scalpService.detail(symbol));
    }

    private static Freshness freshness(Instant collectedAt) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(FRESH_WINDOW) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
