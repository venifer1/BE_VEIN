package com.vein.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.CursorUtil;
import com.vein.common.ErrorCode;
import com.vein.notification.NotificationDigestDto.Digest;

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

        Instant now = Instant.now();
        List<Notification> rows = notificationRepository.findPage(
                userId, unreadOnly, now, cursorTs, cursorId, PageRequest.of(0, PAGE_SIZE + 1));

        String nextCursor = null;
        if (rows.size() > PAGE_SIZE) {
            Notification last = rows.get(PAGE_SIZE - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getId());
            rows = rows.subList(0, PAGE_SIZE);
        }

        List<NotificationDto> items = rows.stream().map(NotificationDto::from).toList();
        int unreadCount = (int) notificationRepository.countActiveUnread(userId, now);
        return new NotificationListResult(items, nextCursor, unreadCount);
    }

    private static final int DIGEST_DEFAULT_HOURS = 24;
    private static final int DIGEST_MAX_HOURS = 168;
    private static final int DIGEST_ROW_CAP = 500;

    /**
     * 읽기 시점 알림 요약 (Track A #3). {@code windowHours} 창(1~168, 기본 24) 안의 알림을
     * 분류·집계한다. 저장 데이터를 바꾸지 않는 순수 집계 조회.
     */
    @Transactional(readOnly = true)
    public Digest digest(Long userId, int windowHours) {
        int window = Math.max(1, Math.min(windowHours, DIGEST_MAX_HOURS));
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofHours(window));
        List<Notification> rows = notificationRepository.findSince(
                userId, since, now, PageRequest.of(0, DIGEST_ROW_CAP));
        List<NotificationDigest.Entry> entries = rows.stream()
                .map(n -> new NotificationDigest.Entry(
                        String.valueOf(n.getId()),
                        n.getSignalId(),
                        n.getTitle(),
                        n.getBody(),
                        n.getStatus().name(),
                        n.getStatus() == NotificationStatus.READ,
                        n.getCreatedAt(),
                        n.getHeldUntil()))
                .toList();
        return NotificationDigest.summarize(entries, window, now);
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
        return createInApp(userId, alertId, signalId, title, body, null);
    }

    /**
     * As {@link #createInApp(Long, Long, Long, String, String)} but with optional 스풀링(R46):
     * {@code heldUntil != null}이면 그 시각까지 보류돼 읽기 모델에서 제외되고 웹푸시도 나가지
     * 않는다(창이 끝나면 다음 폴링에 자연히 노출·핑). 보류 알림도 정상 알림이므로 IN_APP 전달
     * 시도는 기록한다(생성 사실).
     */
    public Notification createInApp(Long userId, Long alertId, Long signalId, String title, String body,
                                    Instant heldUntil) {
        Notification notification = Notification.builder()
                .userId(userId)
                .alertId(alertId)
                .signalId(signalId)
                .status(NotificationStatus.CREATED)
                .title(title)
                .body(body)
                .heldUntil(heldUntil)
                .build();
        try {
            Notification saved = notificationRepository.saveAndFlush(notification);
            deliveryAttemptRepository.save(DeliveryAttempt.builder()
                    .notificationId(saved.getId())
                    .channel("IN_APP")
                    .status("OK")
                    .attemptNo(1)
                    .build());
            if (heldUntil == null) {
                webPushService.deliver(saved);
            }
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
