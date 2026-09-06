package com.vein.tvl;

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
 * TVL endpoints (API_CONTRACT §6): DefiLlama protocols/chains + protocol history.
 */
@RestController
@RequestMapping("/api/v1/tvl")
@Tag(name = "TVL", description = "DefiLlama TVL — protocols, chains, history")
public class TvlController {

    /** 60s refresh; consider stale at > 3 min. */
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(3);

    private final TvlService tvlService;

    public TvlController(TvlService tvlService) {
        this.tvlService = tvlService;
    }

    @GetMapping
    @Operation(summary = "TVL list",
            description = "?mode=PROTOCOL|CHAIN & sort=TVL|CHANGE_7D & q=. TVL<$1000 excluded.")
    public ApiResponse<List<TvlDto.Row>> tvl(@RequestParam(required = false) String mode,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) String q) {
        List<TvlDto.Row> data = tvlService.list(mode, sort, q);
        return ApiResponse.of(data, freshness(tvlService.lastCollectedAt()).name());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Protocol TVL history", description = "TVL line points for a protocol.")
    public ApiResponse<TvlDto.History> history(@PathVariable Long id) {
        return ApiResponse.of(tvlService.history(id));
    }

    private static Freshness freshness(Instant collectedAt) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        return Duration.between(collectedAt, Instant.now()).compareTo(FRESH_WINDOW) > 0
                ? Freshness.DELAYED : Freshness.FRESH;
    }
}
