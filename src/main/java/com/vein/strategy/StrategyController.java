package com.vein.strategy;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Saved-strategy CRUD endpoints scoped to the authenticated user (기획서 §12 seed).
 */
@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "Strategy", description = "User-saved backtest strategies")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a backtest config + result as a named strategy")
    public ApiResponse<StrategyDto.Response> create(@RequestBody StrategyDto.CreateRequest request) {
        return ApiResponse.of(strategyService.create(currentUserId(), request));
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's strategies, newest first")
    public ApiResponse<List<StrategyDto.Response>> list() {
        return ApiResponse.list(strategyService.list(currentUserId()), null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one strategy (owner only)")
    public ApiResponse<StrategyDto.Response> get(@PathVariable Long id) {
        return ApiResponse.of(strategyService.get(currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a strategy (owner only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        strategyService.delete(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Re-run a saved strategy's backtest now and record a snapshot (owner only)",
            description = "Loads the saved params as a backtest RunRequest, runs the engine, "
                    + "persists a metrics snapshot to history and updates the strategy's latest "
                    + "metrics. Returns the new snapshot. 400 if the saved params cannot be parsed.")
    public ApiResponse<StrategyDto.RunSnapshot> run(@PathVariable Long id) {
        return ApiResponse.of(strategyService.run(currentUserId(), id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "List performance snapshots for a strategy, newest first (owner only)")
    public ApiResponse<List<StrategyDto.RunSnapshot>> history(
            @PathVariable Long id,
            @RequestParam(name = "limit", required = false, defaultValue = "30") Integer limit) {
        return ApiResponse.list(strategyService.history(currentUserId(), id, limit), null);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
    }
}
