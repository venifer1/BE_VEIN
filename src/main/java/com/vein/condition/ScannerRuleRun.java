package com.vein.condition;

import java.math.BigDecimal;
import java.time.Instant;

import com.vein.common.TimeUtil;

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
@Table(name = "scanner_rule_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScannerRuleRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "evaluated_count", nullable = false)
    private int evaluatedCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "match_rate", nullable = false)
    private BigDecimal matchRate;

    @Column(name = "frequency_grade", nullable = false, length = 16)
    private String frequencyGrade;

    @Column(name = "notification_count", nullable = false)
    private int notificationCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public static ScannerRuleRun create(Long ruleId, Long userId, int evaluatedCount,
                                        int matchedCount, BigDecimal matchRate,
                                        String frequencyGrade, int notificationCount) {
        ScannerRuleRun run = new ScannerRuleRun();
        run.ruleId = ruleId;
        run.userId = userId;
        run.evaluatedCount = evaluatedCount;
        run.matchedCount = matchedCount;
        run.matchRate = matchRate;
        run.frequencyGrade = frequencyGrade;
        run.notificationCount = notificationCount;
        return run;
    }

    public ConditionDto.RuleRunResponse toResponse() {
        return new ConditionDto.RuleRunResponse(
                id,
                evaluatedCount,
                matchedCount,
                matchRate.stripTrailingZeros().toPlainString(),
                frequencyGrade,
                notificationCount,
                TimeUtil.toIso(createdAt));
    }
}
