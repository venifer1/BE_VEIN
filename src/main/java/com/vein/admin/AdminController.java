package com.vein.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.alert.Alert;
import com.vein.alert.AlertRepository;
import com.vein.billing.Entitlements;
import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.condition.ScannerRule;
import com.vein.condition.ScannerRuleMatchRepository;
import com.vein.condition.ScannerRuleRepository;
import com.vein.condition.ScannerRuleRunRepository;
import com.vein.explain.ExplainFeedback;
import com.vein.explain.ExplainFeedbackRepository;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.notification.Notification;
import com.vein.notification.NotificationRepository;
import com.vein.notification.NotificationStatus;
import com.vein.notification.DeliveryAttempt;
import com.vein.notification.DeliveryAttemptRepository;
import com.vein.notification.WebPushSubscriptionRepository;
import com.vein.ops.AuditLog;
import com.vein.ops.AuditLogRepository;
import com.vein.ops.AuditService;
import com.vein.signal.PatternSignalRepository;
import com.vein.user.UserDto;
import com.vein.user.User;
import com.vein.user.UserRepository;
import com.vein.user.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin", description = "Operational overview for authenticated operators")
public class AdminController {

    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final ExplainFeedbackRepository feedbackRepository;
    private final ScannerRuleRepository scannerRuleRepository;
    private final ScannerRuleRunRepository scannerRuleRunRepository;
    private final ScannerRuleMatchRepository scannerRuleMatchRepository;
    private final PatternSignalRepository signalRepository;
    private final InstrumentRepository instrumentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AdminController(UserRepository userRepository,
                           AlertRepository alertRepository,
                           NotificationRepository notificationRepository,
                           DeliveryAttemptRepository deliveryAttemptRepository,
                           WebPushSubscriptionRepository webPushSubscriptionRepository,
                           ExplainFeedbackRepository feedbackRepository,
                           ScannerRuleRepository scannerRuleRepository,
                           ScannerRuleRunRepository scannerRuleRunRepository,
                           ScannerRuleMatchRepository scannerRuleMatchRepository,
                           PatternSignalRepository signalRepository,
                           InstrumentRepository instrumentRepository,
                           AuditLogRepository auditLogRepository,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.webPushSubscriptionRepository = webPushSubscriptionRepository;
        this.feedbackRepository = feedbackRepository;
        this.scannerRuleRepository = scannerRuleRepository;
        this.scannerRuleRunRepository = scannerRuleRunRepository;
        this.scannerRuleMatchRepository = scannerRuleMatchRepository;
        this.signalRepository = signalRepository;
        this.instrumentRepository = instrumentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    public record UserSummary(long total, Map<String, Long> byStatus) {
    }

    public record AlertSummary(long total, long enabled, long disabled) {
    }

    public record NotificationSummary(long total, long unread, Map<String, Long> byStatus,
                                      long deliveryAttempts24h, long failedDeliveries,
                                      long failedWebPush, long activeWebPushSubscriptions,
                                      long inactiveWebPushSubscriptions) {
    }

    public record ExplainSummary(long total, long helpful, String helpfulRate) {
    }

    public record ScannerRuleSummary(long total, long enabled, long disabled,
                                     long activeMatches, long runs24h) {
    }

    public record SignalSummary(long total, Map<String, Long> byStatus) {
    }

    public record InstrumentSummary(long total, Map<String, Long> byMarket) {
    }

    public record AdminOverviewDto(String generatedAt,
                                   UserSummary users,
                                   AlertSummary alerts,
                                   NotificationSummary notifications,
                                   ExplainSummary explain,
                                   ScannerRuleSummary scannerRules,
                                   SignalSummary signals,
                                   InstrumentSummary instruments) {
    }

    public record AdminNotificationDto(Long id, Long userId, Long signalId, Long alertId,
                                       String status, String title, String body,
                                       String createdAt, String readAt) {
        static AdminNotificationDto from(Notification notification) {
            return new AdminNotificationDto(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getSignalId(),
                    notification.getAlertId(),
                    notification.getStatus().name(),
                    notification.getTitle(),
                    notification.getBody(),
                    TimeUtil.toIso(notification.getCreatedAt()),
                    TimeUtil.toIso(notification.getReadAt()));
        }
    }

    public record AdminDeliveryAttemptDto(Long id, Long notificationId, String channel,
                                          String status, int attemptNo,
                                          String errorCode, String attemptedAt) {
        static AdminDeliveryAttemptDto from(DeliveryAttempt attempt) {
            return new AdminDeliveryAttemptDto(
                    attempt.getId(),
                    attempt.getNotificationId(),
                    attempt.getChannel(),
                    attempt.getStatus(),
                    attempt.getAttemptNo(),
                    attempt.getErrorCode(),
                    TimeUtil.toIso(attempt.getAttemptedAt()));
        }
    }

    public record AuditLogDto(Long id, Long actorId, String action, String target,
                              String ip, String detail, String createdAt) {
        static AuditLogDto from(AuditLog log) {
            return new AuditLogDto(
                    log.getId(),
                    log.getActorId(),
                    log.getAction(),
                    log.getTarget(),
                    log.getIp(),
                    log.getDetail(),
                    TimeUtil.toIso(log.getCreatedAt()));
        }
    }

    @GetMapping("/overview")
    @Operation(summary = "Authenticated operator overview")
    @Transactional(readOnly = true)
    public ApiResponse<AdminOverviewDto> overview() {
        var users = userRepository.findAll();
        var alerts = alertRepository.findAll();
        var notifications = notificationRepository.findAll();
        var feedback = feedbackRepository.findAll();
        var rules = scannerRuleRepository.findAll();
        var signals = signalRepository.findAll();
        var instruments = instrumentRepository.findAll();

        long enabledAlerts = alerts.stream().filter(Alert::isEnabled).count();
        long unreadNotifications = notifications.stream()
                .filter(n -> n.getStatus() != NotificationStatus.READ)
                .count();
        long helpfulFeedback = feedback.stream().filter(ExplainFeedback::isHelpful).count();
        Instant since24h = Instant.now().minusSeconds(86_400);
        long enabledRules = rules.stream().filter(ScannerRule::isEnabled).count();

        AdminOverviewDto dto = new AdminOverviewDto(
                TimeUtil.toIso(Instant.now()),
                new UserSummary(users.size(), countBy(users, u -> u.getStatus().name())),
                new AlertSummary(alerts.size(), enabledAlerts, alerts.size() - enabledAlerts),
                new NotificationSummary(
                        notifications.size(),
                        unreadNotifications,
                        countBy(notifications, n -> n.getStatus().name()),
                        deliveryAttemptRepository.countByAttemptedAtAfter(since24h),
                        deliveryAttemptRepository.countByStatus("FAILED"),
                        deliveryAttemptRepository.countByChannelAndStatus("WEB_PUSH", "FAILED"),
                        webPushSubscriptionRepository.countByActiveTrue(),
                        webPushSubscriptionRepository.countByActiveFalse()),
                new ExplainSummary(
                        feedback.size(),
                        helpfulFeedback,
                        percentage(helpfulFeedback, feedback.size())),
                new ScannerRuleSummary(
                        rules.size(),
                        enabledRules,
                        rules.size() - enabledRules,
                        scannerRuleMatchRepository.countByActiveTrue(),
                        scannerRuleRunRepository.countByCreatedAtAfter(since24h)),
                new SignalSummary(signals.size(), countBy(signals, s -> s.getStatus().name())),
                new InstrumentSummary(instruments.size(), countBy(instruments, Instrument::getMarket)));
        return ApiResponse.of(dto);
    }

    @GetMapping("/users")
    @Operation(summary = "List users for administration")
    @Transactional(readOnly = true)
    public ApiResponse<List<UserDto>> users() {
        List<UserDto> users = userRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) {
                        return Long.compare(a.getId(), b.getId());
                    }
                    if (a.getCreatedAt() == null) {
                        return 1;
                    }
                    if (b.getCreatedAt() == null) {
                        return -1;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(UserDto::from)
                .toList();
        return ApiResponse.of(users);
    }

    @PatchMapping("/users/{id}/approve")
    @Operation(summary = "Approve a pending user")
    @Transactional
    public ApiResponse<UserDto> approveUser(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.of(updateUserStatus(id, UserStatus.APPROVED, request));
    }

    @PatchMapping("/users/{id}/lock")
    @Operation(summary = "Lock a user")
    @Transactional
    public ApiResponse<UserDto> lockUser(@PathVariable Long id, HttpServletRequest request) {
        Long actorId = currentActorId();
        if (actorId.equals(id)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Cannot lock the current admin user");
        }
        return ApiResponse.of(updateUserStatus(id, UserStatus.LOCKED, request));
    }

    @PatchMapping("/users/{id}/tier")
    @Operation(summary = "Set a user's subscription tier (FREE/PRO)")
    @Transactional
    public ApiResponse<UserDto> setUserTier(@PathVariable Long id,
                                            @RequestBody SetTierRequest body,
                                            HttpServletRequest request) {
        String next = body == null ? null : body.tier();
        if (next == null || !(Entitlements.FREE.equalsIgnoreCase(next) || Entitlements.PRO.equalsIgnoreCase(next))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "tier must be FREE or PRO");
        }
        String normalized = Entitlements.normalize(next);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String previous = user.getTier();
        user.updateTier(normalized);
        User saved = userRepository.save(user);
        auditService.record(currentActorId(), "USER_TIER_UPDATE", "user:" + id, clientIp(request),
                Map.of("email", user.getEmail(), "from", String.valueOf(previous), "to", normalized));
        return ApiResponse.of(UserDto.from(saved));
    }

    /** Body for PATCH /users/{id}/tier. */
    public record SetTierRequest(String tier) {
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Recent audit logs")
    @Transactional(readOnly = true)
    public ApiResponse<List<AuditLogDto>> auditLogs() {
        List<AuditLogDto> logs = auditLogRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, 30))
                .stream()
                .map(AuditLogDto::from)
                .toList();
        return ApiResponse.of(logs);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Recent notifications for administration")
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminNotificationDto>> notifications() {
        List<AdminNotificationDto> rows = notificationRepository
                .findByOrderByCreatedAtDesc(PageRequest.of(0, 30))
                .stream()
                .map(AdminNotificationDto::from)
                .toList();
        return ApiResponse.of(rows);
    }

    @GetMapping("/delivery-attempts/failed")
    @Operation(summary = "Recent failed delivery attempts")
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminDeliveryAttemptDto>> failedDeliveries() {
        List<AdminDeliveryAttemptDto> rows = deliveryAttemptRepository
                .findByStatusOrderByAttemptedAtDesc("FAILED", PageRequest.of(0, 30))
                .stream()
                .map(AdminDeliveryAttemptDto::from)
                .toList();
        return ApiResponse.of(rows);
    }

    private UserDto updateUserStatus(Long id, UserStatus next, HttpServletRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        UserStatus previous = user.getStatus();
        user.updateStatus(next);
        User saved = userRepository.save(user);
        auditService.record(currentActorId(), "USER_STATUS_UPDATE", "user:" + id, clientIp(request),
                Map.of("email", user.getEmail(), "from", previous.name(), "to", next.name()));
        return UserDto.from(saved);
    }

    private static Long currentActorId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static <T> Map<String, Long> countBy(Iterable<T> rows, Function<T, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T row : rows) {
            String key = classifier.apply(row);
            if (key == null || key.isBlank()) {
                key = "UNKNOWN";
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static String percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
