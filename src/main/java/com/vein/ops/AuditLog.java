package com.vein.ops;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only audit record. Maps the Flyway-owned {@code audit_logs} table;
 * {@code created_at} defaults to {@code now()} in the database.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(length = 64)
    private String target;

    @Column(length = 45)
    private String ip;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    private AuditLog(Long actorId, String action, String target, String ip, String detail) {
        this.actorId = actorId;
        this.action = action;
        this.target = target;
        this.ip = ip;
        this.detail = detail;
    }

    public static AuditLog of(Long actorId, String action, String target, String ip, String detail) {
        return new AuditLog(actorId, action, target, ip, detail);
    }
}
