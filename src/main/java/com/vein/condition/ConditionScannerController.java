package com.vein.condition;

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

@RestController
@RequestMapping("/api/v1/scanner")
@Tag(name = "Condition Scanner", description = "On-demand technical condition scanner")
public class ConditionScannerController {

    private final ConditionScannerService service;

    public ConditionScannerController(ConditionScannerService service) {
        this.service = service;
    }

    @PostMapping("/run")
    @Operation(summary = "Run a condition expression against an active market universe")
    public ApiResponse<ConditionDto.RunResponse> run(@RequestBody ConditionDto.RunRequest request) {
        return ApiResponse.of(service.run(request));
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a named condition expression")
    public ApiResponse<ConditionDto.RuleResponse> save(@RequestBody ConditionDto.SaveRequest request) {
        return ApiResponse.of(service.save(userId(), request));
    }

    @GetMapping("/rules")
    @Operation(summary = "List the current user's saved condition expressions")
    public ApiResponse<List<ConditionDto.RuleResponse>> list() {
        return ApiResponse.list(service.list(userId()), null);
    }

    @PatchMapping("/rules/{id}")
    @Operation(summary = "Update a saved condition expression")
    public ApiResponse<ConditionDto.RuleResponse> update(@PathVariable Long id,
                                                         @RequestBody ConditionDto.UpdateRuleRequest request) {
        return ApiResponse.of(service.update(userId(), id, request));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a saved condition expression")
    public void delete(@PathVariable Long id) {
        service.delete(userId(), id);
    }

    @PostMapping("/rules/{id}/simulate")
    @Operation(summary = "Simulate a saved rule against the current market snapshot")
    public ApiResponse<ConditionDto.SimulationResponse> simulate(@PathVariable Long id) {
        return ApiResponse.of(service.simulate(userId(), id));
    }

    @GetMapping("/rules/{id}/history")
    @Operation(summary = "List recent scheduled evaluations for a saved rule")
    public ApiResponse<List<ConditionDto.RuleRunResponse>> history(@PathVariable Long id) {
        return ApiResponse.of(service.history(userId(), id));
    }

    private Long userId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
