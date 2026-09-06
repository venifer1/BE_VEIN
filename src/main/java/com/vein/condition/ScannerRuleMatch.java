package com.vein.condition;

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
@Table(name = "scanner_rule_matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScannerRuleMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "first_matched_at", nullable = false)
    private Instant firstMatchedAt;

    @Column(name = "last_matched_at", nullable = false)
    private Instant lastMatchedAt;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    public static ScannerRuleMatch create(Long ruleId, Long userId, Long instrumentId, Instant now) {
        ScannerRuleMatch match = new ScannerRuleMatch();
        match.ruleId = ruleId;
        match.userId = userId;
        match.instrumentId = instrumentId;
        match.active = true;
        match.firstMatchedAt = now;
        match.lastMatchedAt = now;
        return match;
    }

    public boolean reactivate(Instant now) {
        boolean wasInactive = !active;
        active = true;
        if (wasInactive) {
            firstMatchedAt = now;
            lastNotifiedAt = null;
        }
        lastMatchedAt = now;
        return wasInactive;
    }

    public void deactivate() {
        active = false;
    }

    public void markNotified(Instant now) {
        lastNotifiedAt = now;
    }
}
