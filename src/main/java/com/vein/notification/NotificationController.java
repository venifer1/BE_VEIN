package com.vein.notification;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.notification.NotificationDigestDto.Digest;
import com.vein.notification.NotificationService.NotificationListResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * In-app notification feed endpoints scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "In-app notification feed")
public class NotificationController {

    private final NotificationService notificationService;
    private final WebPushService webPushService;

    public NotificationController(NotificationService notificationService,
                                  WebPushService webPushService) {
        this.notificationService = notificationService;
        this.webPushService = webPushService;
    }

    @GetMapping
    @Operation(summary = "List notifications (newest-first, cursor paged)")
    public ApiResponse<List<NotificationDto>> list(
            @RequestParam(value = "unread_only", defaultValue = "false") boolean unreadOnly,
            @RequestParam(value = "cursor", required = false) String cursor) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        NotificationListResult result = notificationService.list(userId, unreadOnly, cursor);
        return ApiResponse.list(result.items(), result.nextCursor(), result.unreadCount());
    }

    @GetMapping("/digest")
    @Operation(summary = "알림 다이제스트(읽기 시점 요약)",
            description = "지정 창(window 시간, 기본 24 · 최대 168) 안의 알림을 분류(패턴 신호·조건검색·"
                    + "청산 급증·시스템)별로 집계하고 안읽은 최신 표본과 요약 문장을 반환. 저장 데이터를 "
                    + "바꾸지 않는 순수 조회. (Track A #3, 알림 노이즈 완화)")
    public ApiResponse<Digest> digest(
            @RequestParam(value = "window", defaultValue = "24") int window) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(notificationService.digest(userId, window));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ApiResponse<NotificationDto> markRead(@PathVariable Long id) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(notificationService.markRead(userId, id));
    }

    @PostMapping("/{id}/deliveries/web-push")
    @Operation(summary = "Acknowledge browser display of a notification")
    public ApiResponse<Void> recordWebPushDelivery(@PathVariable Long id) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        notificationService.recordWebPushDelivery(userId, id);
        return ApiResponse.of(null);
    }

    @GetMapping("/web-push/config")
    @Operation(summary = "Return web-push VAPID public key")
    public ApiResponse<WebPushDto.Config> webPushConfig() {
        return ApiResponse.of(webPushService.config());
    }

    @PutMapping("/web-push/subscription")
    @Operation(summary = "Create or replace a browser web-push subscription")
    public ApiResponse<WebPushDto.SubscriptionResponse> subscribe(
            @Valid @RequestBody WebPushDto.SubscriptionRequest request) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ApiResponse.of(webPushService.subscribe(userId, request));
    }

    @DeleteMapping("/web-push/subscription")
    @Operation(summary = "Deactivate browser web-push subscription")
    public ApiResponse<Void> unsubscribe(@RequestParam(value = "endpoint", required = false) String endpoint) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        webPushService.unsubscribe(userId, endpoint);
        return ApiResponse.of(null);
    }
}
