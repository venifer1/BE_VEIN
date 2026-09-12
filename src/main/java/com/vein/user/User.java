package com.vein.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /** Attribution: which content/campaign drove this signup (utm_source). Nullable. */
    @Column(name = "signup_source", length = 64, updatable = false)
    private String signupSource;

    /** Attribution: HTTP referrer at signup. Nullable. */
    @Column(name = "signup_referrer", length = 255, updatable = false)
    private String signupReferrer;

    /** 온보딩 "시작하기" 카드를 닫은 시각(R48). NULL이면 아직 안 닫음. */
    @Column(name = "onboarding_dismissed_at")
    private Instant onboardingDismissedAt;

    /** New public-signup user (MONETIZATION 단계2 ①). Role fixed to TESTER; status set by caller. */
    public static User create(String email, String passwordHash, String role, UserStatus status,
                              String signupSource, String signupReferrer) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.role = role;
        u.status = status;
        u.signupSource = signupSource;
        u.signupReferrer = signupReferrer;
        return u;
    }

    public boolean isApproved() {
        return status == UserStatus.APPROVED;
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    /** 온보딩 카드를 닫는다(R48). 멱등 — 이미 닫혔으면 시각을 유지한다. */
    public void dismissOnboarding(Instant now) {
        if (this.onboardingDismissedAt == null) {
            this.onboardingDismissedAt = now;
        }
    }
}
