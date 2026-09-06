package com.vein.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Cursor page, newest-first. When {@code unreadOnly} is true, READ notifications
     * are excluded. When cursor params are null, returns the first page.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (:unreadOnly = false OR n.status <> com.vein.notification.NotificationStatus.READ)
              AND (cast(:cursorTs as Instant) IS NULL
                   OR n.createdAt < :cursorTs
                   OR (n.createdAt = :cursorTs AND n.id < :cursorId))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findPage(@Param("userId") Long userId,
                                @Param("unreadOnly") boolean unreadOnly,
                                @Param("cursorTs") Instant cursorTs,
                                @Param("cursorId") Long cursorId,
                                Pageable pageable);

    long countByUserIdAndStatusNot(Long userId, NotificationStatus status);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    List<Notification> findByOrderByCreatedAtDesc(Pageable pageable);
}
