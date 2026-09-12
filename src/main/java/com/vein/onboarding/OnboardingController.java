package com.vein.onboarding;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.onboarding.OnboardingDto.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 온보딩 "시작하기" 체크리스트 (Track B #2). 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/me/onboarding")
@Tag(name = "Onboarding", description = "신규 사용자 시작하기 체크리스트")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    private static Long currentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    @Operation(summary = "온보딩 상태",
            description = "관심종목·알림·모의투자·조건검색 스텝의 완료 여부(실제 데이터에서 파생) + "
                    + "진행률 + 닫힘 여부. all_done이거나 dismissed면 FE는 카드를 감춘다.")
    public ApiResponse<Status> status() {
        return ApiResponse.of(onboardingService.status(currentUserId()));
    }

    @PostMapping("/dismiss")
    @Operation(summary = "온보딩 카드 닫기", description = "다시 띄우지 않도록 닫은 시각을 기록(멱등).")
    public ApiResponse<Status> dismiss() {
        Long userId = currentUserId();
        onboardingService.dismiss(userId);
        return ApiResponse.of(onboardingService.status(userId));
    }
}
