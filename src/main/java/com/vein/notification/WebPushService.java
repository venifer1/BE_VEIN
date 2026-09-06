package com.vein.notification;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Instant;
import java.util.List;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;

import nl.martijndwars.webpush.PushService;

@Service
@Transactional
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private static final String WEB_PUSH_CHANNEL = "WEB_PUSH";

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final int ttlSec;

    public WebPushService(WebPushSubscriptionRepository subscriptionRepository,
                          DeliveryAttemptRepository deliveryAttemptRepository,
                          ObjectMapper objectMapper,
                          @Value("${vein.web-push.public-key:}") String publicKey,
                          @Value("${vein.web-push.private-key:}") String privateKey,
                          @Value("${vein.web-push.subject:mailto:admin@vein.local}") String subject,
                          @Value("${vein.web-push.ttl-sec:3600}") int ttlSec) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.objectMapper = objectMapper;
        this.publicKey = normalize(publicKey);
        this.privateKey = normalize(privateKey);
        this.subject = subject;
        this.ttlSec = ttlSec;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public WebPushDto.Config config() {
        return new WebPushDto.Config(isConfigured(), isConfigured() ? publicKey : null);
    }

    public WebPushDto.SubscriptionResponse subscribe(Long userId, WebPushDto.SubscriptionRequest request) {
        if (!isConfigured()) {
            throw new ApiException(ErrorCode.STATUS_UNAVAILABLE, "Web push is not configured");
        }
        String endpoint = request.endpoint().trim();
        String p256dh = request.keys().p256dh().trim();
        String auth = request.keys().auth().trim();
        WebPushSubscription sub = subscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(() -> WebPushSubscription.create(
                        userId, endpoint, p256dh, auth, trimToNull(request.userAgent())));
        if (!sub.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Subscription belongs to another user");
        }
        sub.update(p256dh, auth, trimToNull(request.userAgent()));
        subscriptionRepository.save(sub);
        return new WebPushDto.SubscriptionResponse(true);
    }

    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            subscriptionRepository.findByUserIdAndActiveTrue(userId)
                    .forEach(WebPushSubscription::deactivate);
            return;
        }
        subscriptionRepository.findByUserIdAndEndpoint(userId, endpoint.trim())
                .ifPresent(WebPushSubscription::deactivate);
    }

    public void deliver(Notification notification) {
        if (!isConfigured() || notification == null) {
            return;
        }
        List<WebPushSubscription> subscriptions =
                subscriptionRepository.findByUserIdAndActiveTrue(notification.getUserId());
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = payload(notification);
        boolean anySuccess = false;
        for (WebPushSubscription sub : subscriptions) {
            try {
                PushService pushService = new PushService(publicKey, privateKey, subject);
                nl.martijndwars.webpush.Notification pushNotification =
                        new nl.martijndwars.webpush.Notification(
                                sub.getEndpoint(),
                                sub.getP256dh(),
                                sub.getAuth(),
                                payload.getBytes(StandardCharsets.UTF_8),
                                ttlSec);
                HttpResponse response = pushService.send(pushNotification);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    sub.markSuccess(Instant.now());
                    anySuccess = true;
                } else {
                    boolean gone = status == 404 || status == 410;
                    sub.markFailure(Instant.now(), gone);
                    log.warn("Web push delivery failed notification={} endpointStatus={}",
                            notification.getId(), status);
                }
            } catch (Exception e) {
                sub.markFailure(Instant.now(), false);
                log.warn("Web push delivery failed notification={}", notification.getId(), e);
            }
        }
        recordAttempt(notification.getId(), anySuccess ? "OK" : "FAILED");
    }

    private String payload(Notification notification) {
        try {
            return objectMapper.writeValueAsString(new PushPayload(
                    String.valueOf(notification.getId()),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getSignalId() == null ? "/settings" : "/signals/" + notification.getSignalId()));
        } catch (JsonProcessingException e) {
            return "{\"title\":\"VEIN notification\",\"url\":\"/settings\"}";
        }
    }

    private void recordAttempt(Long notificationId, String status) {
        if (deliveryAttemptRepository.existsByNotificationIdAndChannel(notificationId, WEB_PUSH_CHANNEL)) {
            return;
        }
        try {
            deliveryAttemptRepository.saveAndFlush(DeliveryAttempt.builder()
                    .notificationId(notificationId)
                    .channel(WEB_PUSH_CHANNEL)
                    .status(status)
                    .attemptNo(1)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Another browser or foreground acknowledgement recorded the channel.
        }
    }

    private boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record PushPayload(String notificationId, String title, String body, String url) {
    }
}
