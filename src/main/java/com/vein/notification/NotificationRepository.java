package com.vein.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 알림 규칙 삭제 전 FK(notifications.alert_id) 언링크(R62). 알림 이력은 보존. */
    @Modifying
    @Query("update Notification n set n.alertId = null where n.alertId = :alertId")
    int clearAlertId(@Param("alertId") Long alertId);

    /**
     * Cursor page, newest-first. When {@code unreadOnly} is true, READ notifications
     * are excluded. When cursor params are null, returns the first page. 스풀링(R46):
     * 아직 보류 중인 알림(held_until > now)은 창이 끝날 때까지 제외한다.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (:unreadOnly = false OR n.status <> com.vein.notification.NotificationStatus.READ)
              AND (n.heldUntil IS NULL OR n.heldUntil <= :now)
              AND (cast(:cursorTs as Instant) IS NULL
                   OR n.createdAt < :cursorTs
                   OR (n.createdAt = :cursorTs AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findPage(@Param("userId") Long userId,
                                @Param("unreadOnly") boolean unreadOnly,
                                @Param("now") Instant now,
                                @Param("cursorTs") Instant cursorTs,
                                @Param("cursorId") Long cursorId,
                                Pageable pageable);

    /**
     * 다이제스트용: 창 시작 이후 알림을 최신순으로. Pageable로 상한을 건다.
     * 스풀링(R46): 아직 보류 중인 알림은 제외한다.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.createdAt >= :since
              AND (n.heldUntil IS NULL OR n.heldUntil <= :now)
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findSince(@Param("userId") Long userId,
                                 @Param("since") Instant since,
                                 @Param("now") Instant now,
                                 Pageable pageable);

    /** 활성 안읽음 수. 스풀링(R46): 보류 중(held_until > now)은 세지 않는다. */
    @Query("""
            SELECT count(n) FROM Notification n
            WHERE n.userId = :userId
              AND n.status <> com.vein.notification.NotificationStatus.READ
              AND (n.heldUntil IS NULL OR n.heldUntil <= :now)
            """)
    long countActiveUnread(@Param("userId") Long userId, @Param("now") Instant now);

    long countByUserIdAndStatusNot(Long userId, NotificationStatus status);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    List<Notification> findByOrderByCreatedAtDesc(Pageable pageable);
}
