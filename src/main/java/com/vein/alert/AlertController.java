package com.vein.alert;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Alert CRUD endpoints scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alert", description = "User-configured signal alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's alert rules")
    public ApiResponse<List<AlertDto>> list() {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.list(alertService.list(userId), null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an alert")
    public ApiResponse<AlertDto> create(@Valid @RequestBody CreateAlertRequest request) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(alertService.create(
                userId, request.instrumentId(), request.signalType(),
                request.timeframe(), request.market(), request.cooldownSec()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an alert (enable/disable, cooldown)")
    public ApiResponse<AlertDto> update(@PathVariable Long id, @Valid @RequestBody UpdateAlertRequest request) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(alertService.update(userId, id, request.enabled(), request.cooldownSec()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an alert rule")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        alertService.delete(userId, id);
        return ApiResponse.of(null);
    }

    /** Request body for POST; client sends snake_case fields. */
    public record CreateAlertRequest(@NotNull Long instrumentId, @NotNull String signalType, String timeframe,
                                     String market, Integer cooldownSec) {
    }

    /** Request body for PATCH; all fields optional. */
    public record UpdateAlertRequest(Boolean enabled, Integer cooldownSec) {
    }
}
