package com.vein.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.CursorUtil;
import com.vein.common.ErrorCode;

/**
 * Notification read-model queries plus in-app creation with idempotent dedup.
 */
@Service
@Transactional
public class NotificationService {

    private static final int PAGE_SIZE = 20;
    private static final String WEB_PUSH_CHANNEL = "WEB_PUSH";

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebPushService webPushService;

    public NotificationService(NotificationRepository notificationRepository,
                               DeliveryAttemptRepository deliveryAttemptRepository,
                               WebPushService webPushService) {
        this.notificationRepository = notificationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.webPushService = webPushService;
    }

    public record NotificationListResult(List<NotificationDto> items, String nextCursor, int unreadCount) {
    }

    @Transactional(readOnly = true)
    public NotificationListResult list(Long userId, boolean unreadOnly, String cursor) {
        Instant cursorTs = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorUtil.Decoded decoded = CursorUtil.decode(cursor);
            cursorTs = decoded.ts();
            cursorId = decoded.id();
        }

        List<Notification> rows = notificationRepository.findPage(
                userId, unreadOnly, cursorTs, cursorId, PageRequest.of(0, PAGE_SIZE + 1));

        String nextCursor = null;
        if (rows.size() > PAGE_SIZE) {
            Notification last = rows.get(PAGE_SIZE - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getId());
            rows = rows.subList(0, PAGE_SIZE);
        }

        List<NotificationDto> items = rows.stream().map(NotificationDto::from).toList();
        int unreadCount = (int) notificationRepository.countByUserIdAndStatusNot(userId, NotificationStatus.READ);
        return new NotificationListResult(items, nextCursor, unreadCount);
    }

    public NotificationDto markRead(Long userId, Long id) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead(Instant.now());
        return NotificationDto.from(notificationRepository.save(n));
    }

    /**
     * Record that the authenticated user's browser displayed this notification.
     * Repeated client acknowledgements are idempotent.
     */
    public void recordWebPushDelivery(Long userId, Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (deliveryAttemptRepository.existsByNotificationIdAndChannel(
                notification.getId(), WEB_PUSH_CHANNEL)) {
            return;
        }
        try {
            deliveryAttemptRepository.saveAndFlush(DeliveryAttempt.builder()
                    .notificationId(notification.getId())
                    .channel(WEB_PUSH_CHANNEL)
                    .status("OK")
                    .attemptNo(1)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent acknowledgement: the unique channel index makes this a no-op.
        }
    }

    /**
     * Create an in-app notification + a successful delivery attempt. Idempotent on
     * the (user_id, alert_id, signal_id) unique key: returns null when a duplicate
     * already exists.
     */
    public Notification createInApp(Long userId, Long alertId, Long signalId, String title, String body) {
        Notification notification = Notification.builder()
                .userId(userId)
                .alertId(alertId)
                .signalId(signalId)
                .status(NotificationStatus.CREATED)
                .title(title)
                .body(body)
                .build();
        try {
            Notification saved = notificationRepository.saveAndFlush(notification);
            deliveryAttemptRepository.save(DeliveryAttempt.builder()
                    .notificationId(saved.getId())
                    .channel("IN_APP")
                    .status("OK")
                    .attemptNo(1)
                    .build());
            webPushService.deliver(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Duplicate (user_id, alert_id, signal_id) — dedup, idempotent no-op.
            return null;
        }
    }

    public Notification createSystemInApp(Long userId, String title, String body) {
        Notification notification = Notification.builder()
                .userId(userId)
                .status(NotificationStatus.CREATED)
                .title(title)
                .body(body)
                .build();
        Notification saved = notificationRepository.saveAndFlush(notification);
        deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .notificationId(saved.getId())
                .channel("IN_APP")
                .status("OK")
                .attemptNo(1)
                .build());
        webPushService.deliver(saved);
        return saved;
    }
}
