package com.vein.notification;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 내 알림 환경설정 (R42). 조용한 시간(Quiet Hours) 조회/수정. 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/me/notification-prefs")
@Tag(name = "NotificationPrefs", description = "알림 환경설정 · 조용한 시간")
public class NotificationPrefController {

    private final NotificationPrefService service;

    public NotificationPrefController(NotificationPrefService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "내 알림 환경설정 조회",
            description = "조용한 시간(quiet_enabled, KST quiet_start_hour/quiet_end_hour). 미설정 시 기본값(22~8, 비활성).")
    public ApiResponse<NotificationPrefDto> get() {
        return ApiResponse.of(NotificationPrefDto.from(service.getOrDefault(currentUserId())));
    }

    @PutMapping
    @Operation(summary = "내 알림 환경설정 수정",
            description = "조용한 시간 창 동안엔 새 알림을 만들지 않아 노이즈를 줄인다. 시각은 KST 시(0-23).")
    public ApiResponse<NotificationPrefDto> update(@RequestBody NotificationPrefDto req) {
        NotificationPref saved = service.update(
                currentUserId(), req.quietEnabled(), req.quietStartHour(), req.quietEndHour());
        return ApiResponse.of(NotificationPrefDto.from(saved));
    }

    private static Long currentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
