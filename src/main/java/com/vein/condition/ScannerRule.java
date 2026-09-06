package com.vein.condition;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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
@Table(name = "scanner_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScannerRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false, length = 8)
    private String timeframe;

    @Column(nullable = false, length = 4)
    private String logic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode conditions;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ScannerRule create(Long userId, String name, String market,
                                     String timeframe, String logic, JsonNode conditions) {
        ScannerRule rule = new ScannerRule();
        rule.userId = userId;
        rule.name = name;
        rule.market = market;
        rule.timeframe = timeframe;
        rule.logic = logic;
        rule.conditions = conditions;
        rule.enabled = true;
        rule.updatedAt = Instant.now();
        return rule;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
