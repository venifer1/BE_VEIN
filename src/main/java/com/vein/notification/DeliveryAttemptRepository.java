package com.vein.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    boolean existsByNotificationIdAndChannel(Long notificationId, String channel);

    long countByStatus(String status);

    long countByChannelAndStatus(String channel, String status);

    long countByAttemptedAtAfter(Instant since);

    List<DeliveryAttempt> findByStatusOrderByAttemptedAtDesc(String status, Pageable pageable);
}
