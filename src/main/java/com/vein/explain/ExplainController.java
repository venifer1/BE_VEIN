package com.vein.explain;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/explain")
@Tag(name = "Explain", description = "Explain quality feedback")
public class ExplainController {

    private final ExplainFeedbackService service;

    public ExplainController(ExplainFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/{signalId}/feedback")
    @Operation(summary = "Get Explain feedback summary and the current user's choice")
    public ApiResponse<ExplainFeedbackService.Summary> summary(@PathVariable Long signalId) {
        return ApiResponse.of(service.summary(currentUserId(), signalId));
    }

    @PostMapping("/{signalId}/feedback")
    @Operation(summary = "Create or replace the current user's Explain feedback")
    public ApiResponse<ExplainFeedbackService.Summary> submit(
            @PathVariable Long signalId,
            @Valid @RequestBody ExplainFeedbackService.Request request) {
        return ApiResponse.of(service.submit(currentUserId(), signalId, request));
    }

    private Long currentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
