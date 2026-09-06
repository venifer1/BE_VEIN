package com.vein.explain;

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
@Table(name = "explain_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplainFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(nullable = false)
    private boolean helpful;

    @Column(length = 48)
    private String reason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ExplainFeedback create(Long userId, Long signalId,
                                         boolean helpful, String reason) {
        ExplainFeedback feedback = new ExplainFeedback();
        feedback.userId = userId;
        feedback.signalId = signalId;
        feedback.update(helpful, reason);
        return feedback;
    }

    public void update(boolean helpful, String reason) {
        this.helpful = helpful;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.updatedAt = Instant.now();
    }
}
