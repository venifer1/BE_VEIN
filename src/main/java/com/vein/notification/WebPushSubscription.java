package com.vein.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "web_push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "text")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WebPushSubscription create(Long userId, String endpoint, String p256dh,
                                             String auth, String userAgent) {
        WebPushSubscription sub = new WebPushSubscription();
        sub.userId = userId;
        sub.endpoint = endpoint;
        sub.update(p256dh, auth, userAgent);
        return sub;
    }

    public void update(String p256dh, String auth, String userAgent) {
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void markSuccess(Instant now) {
        this.lastSuccessAt = now;
        this.active = true;
        this.updatedAt = now;
    }

    public void markFailure(Instant now, boolean deactivate) {
        this.lastFailureAt = now;
        if (deactivate) {
            this.active = false;
        }
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
