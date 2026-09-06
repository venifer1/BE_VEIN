package com.vein.signal;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;
import com.vein.explain.SignalExplainDto;
import com.vein.explain.SignalExplainService;
import com.vein.signal.SignalService.SignalListResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read endpoints for pattern signals (부록 C / 표 14).
 */
@RestController
@RequestMapping("/api/v1/signals")
@Tag(name = "Signals", description = "Detected pattern signals")
public class SignalController {

    private final SignalService signalService;
    private final SignalPerformanceService performanceService;
    private final SignalExplainService explainService;

    public SignalController(SignalService signalService,
                            SignalPerformanceService performanceService,
                            SignalExplainService explainService) {
        this.signalService = signalService;
        this.performanceService = performanceService;
        this.explainService = explainService;
    }

    @GetMapping
    @Operation(summary = "List signals",
            description = "Cursor-paginated, newest first. Supports type/timeframe/instrument/"
                    + "status filters and watchlist_only scoping.")
    public ApiResponse<List<SignalDto>> list(
            @RequestParam(value = "type", required = false) SignalType type,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "timeframe", required = false) String timeframe,
            @RequestParam(value = "instrument_id", required = false) Long instrumentId,
            @RequestParam(value = "watchlist_only", required = false, defaultValue = "false")
            boolean watchlistOnly,
            @RequestParam(value = "status", required = false) SignalStatus status,
            @RequestParam(value = "near_only", required = false, defaultValue = "false")
            boolean nearOnly,
            @RequestParam(value = "cursor", required = false) String cursor) {

        SignalListResult result = signalService.list(
                type, market, timeframe, instrumentId, watchlistOnly, status, nearOnly, cursor,
                watchlistOnly ? currentUserId() : null);
        return ApiResponse.list(result.items(), result.nextCursor());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get signal detail",
            description = "Returns evidence and invalidation for a single signal.")
    public ApiResponse<SignalDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.of(signalService.detail(id));
    }

    @GetMapping("/{id}/performance")
    @Operation(summary = "Get signal performance",
            description = "How the pattern actually performed after detection (기획서 §31): "
                    + "for each elapsed horizon (1h/4h/1d/3d/7d), price_at_horizon, return_pct, "
                    + "and max favorable/adverse excursion (mfe/mae). The horizons array contains "
                    + "only the computed ones (empty if the signal is too fresh). Accepts the bare "
                    + "numeric id.")
    public ApiResponse<SignalPerformanceDto.Detail> performance(@PathVariable Long id) {
        return ApiResponse.of(performanceService.detail(id));
    }

    @GetMapping("/{id}/explain")
    @Operation(summary = "Get rule-based Pattern Score and Explain")
    public ApiResponse<SignalExplainDto> explain(@PathVariable Long id) {
        return ApiResponse.of(explainService.explain(id));
    }

    @GetMapping("/performance/summary")
    @Operation(summary = "Signal performance summary",
            description = "Aggregate hit-rate / return stats for a horizon, grouped by "
                    + "(type, market, timeframe). hit_rate = % of samples with positive return. "
                    + "Powers the \"이 패턴 historically X% 적중\" UI. Filters optional; empty when no data.")
    public ApiResponse<List<SignalPerformanceDto.SummaryRow>> performanceSummary(
            @RequestParam(value = "type", required = false) SignalType type,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "timeframe", required = false) String timeframe,
            @Parameter(description = "1h|4h|1d|3d|7d (default 1d)")
            @RequestParam(value = "horizon", required = false, defaultValue = "1d") String horizon) {
        return ApiResponse.of(performanceService.summary(type, market, timeframe, horizon));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
    }
}
