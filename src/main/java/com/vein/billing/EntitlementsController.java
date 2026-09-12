package com.vein.billing;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.billing.EntitlementsDto.Status;
import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 구독 엔타이틀먼트 조회 (Track C 착수, R52). 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/me/entitlements")
@Tag(name = "Billing", description = "구독 티어 · 엔타이틀먼트(FREE/PRO)")
public class EntitlementsController {

    private final EntitlementsService entitlementsService;

    public EntitlementsController(EntitlementsService entitlementsService) {
        this.entitlementsService = entitlementsService;
    }

    @GetMapping
    @Operation(summary = "내 구독 티어와 기능/한도",
            description = "현재 티어(FREE/PRO)와 기능별 한도(-1=무제한)를 반환. 결제 연동은 후속.")
    public ApiResponse<Status> entitlements() {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(entitlementsService.entitlements(userId));
    }
}
