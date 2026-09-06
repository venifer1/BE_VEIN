package com.vein.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {
    List<WebPushSubscription> findByUserIdAndActiveTrue(Long userId);

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    Optional<WebPushSubscription> findByUserIdAndEndpoint(Long userId, String endpoint);

    long countByActiveTrue();

    long countByActiveFalse();
}
